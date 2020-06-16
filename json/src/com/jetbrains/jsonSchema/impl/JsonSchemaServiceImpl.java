// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.jsonSchema.JsonPointerUtil;
import com.jetbrains.jsonSchema.JsonSchemaCatalogEntry;
import com.jetbrains.jsonSchema.JsonSchemaCatalogProjectConfiguration;
import com.jetbrains.jsonSchema.JsonSchemaVfsListener;
import com.jetbrains.jsonSchema.extension.*;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.remote.JsonFileResolver;
import com.jetbrains.jsonSchema.remote.JsonSchemaCatalogExclusion;
import com.jetbrains.jsonSchema.remote.JsonSchemaCatalogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class JsonSchemaServiceImpl implements JsonSchemaService, ModificationTracker, Disposable {
  private static final Logger LOG = Logger.getInstance(JsonSchemaServiceImpl.class);

  @NotNull private final Project myProject;
  @NotNull private final MyState myState;
  @NotNull private final ClearableLazyValue<Set<String>> myBuiltInSchemaIds;
  @NotNull private final Set<String> myRefs = ContainerUtil.newConcurrentSet();
  private final AtomicLong myAnyChangeCount = new AtomicLong(0);

  @NotNull private final JsonSchemaCatalogManager myCatalogManager;

  public JsonSchemaServiceImpl(@NotNull Project project) {
    myProject = project;
    myState = new MyState(() -> getProvidersFromFactories(), myProject);
    myBuiltInSchemaIds = new ClearableLazyValue<Set<String>>() {
      @NotNull
      @Override
      protected Set<String> compute() {
        return ContainerUtil.map2SetNotNull(myState.getFiles(), f -> JsonCachedValues.getSchemaId(f, myProject));
      }
    };
    JsonSchemaProviderFactory.EP_NAME.addChangeListener(this::reset, this);
    JsonSchemaEnabler.EXTENSION_POINT_NAME.addChangeListener(this::reset, this);
    JsonSchemaCatalogExclusion.EP_NAME.addChangeListener(this::reset, this);

    myCatalogManager = new JsonSchemaCatalogManager(myProject);

    MessageBusConnection connection = project.getMessageBus().connect(this);
    connection.subscribe(JsonSchemaVfsListener.JSON_SCHEMA_CHANGED, myAnyChangeCount::incrementAndGet);
    connection.subscribe(JsonSchemaVfsListener.JSON_DEPS_CHANGED, () -> {
      myRefs.clear();
      myAnyChangeCount.incrementAndGet();
    });
    JsonSchemaVfsListener.startListening(project, this, connection);
    myCatalogManager.startUpdates();
  }

  @Override
  public long getModificationCount() {
    return myAnyChangeCount.get();
  }

  @Override
  public void dispose() {
  }

  @NotNull
  private List<JsonSchemaFileProvider> getProvidersFromFactories() {
    List<JsonSchemaFileProvider> providers = new ArrayList<>();
    for (JsonSchemaProviderFactory factory : getProviderFactories()) {
      try {
        providers.addAll(factory.getProviders(myProject));
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        PluginException.logPluginError(Logger.getInstance(JsonSchemaService.class), e.toString(), e, factory.getClass());
      }
    }
    return providers;
  }

  @NotNull
  protected List<JsonSchemaProviderFactory> getProviderFactories() {
    return JsonSchemaProviderFactory.EP_NAME.getExtensionList();
  }

  @Nullable
  @Override
  public JsonSchemaFileProvider getSchemaProvider(@NotNull VirtualFile schemaFile) {
    return myState.getProvider(schemaFile);
  }

  @Nullable
  @Override
  public JsonSchemaFileProvider getSchemaProvider(@NotNull JsonSchemaObject schemaObject) {
    VirtualFile file = resolveSchemaFile(schemaObject);
    return file == null ? null : getSchemaProvider(file);
  }

  @Override
  public void reset() {
    myState.reset();
    myBuiltInSchemaIds.drop();
    myAnyChangeCount.incrementAndGet();
    for (Runnable action: myResetActions) {
      action.run();
    }
    DaemonCodeAnalyzer.getInstance(myProject).restart();
  }

  @Override
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  @Nullable
  public VirtualFile findSchemaFileByReference(@NotNull String reference, @Nullable VirtualFile referent) {
    final VirtualFile file = findBuiltInSchemaByReference(reference);
    if (file != null) return file;
    if (reference.startsWith("#")) return referent;
    return JsonFileResolver.resolveSchemaByReference(referent, JsonPointerUtil.normalizeId(reference));
  }

  @Nullable
  private VirtualFile findBuiltInSchemaByReference(@NotNull String reference) {
    String id = JsonPointerUtil.normalizeId(reference);
    if (!myBuiltInSchemaIds.getValue().contains(id)) return null;
    for (VirtualFile file : myState.getFiles()) {
      if (id.equals(JsonCachedValues.getSchemaId(file, myProject))) {
        return file;
      }
    }
    return null;
  }

  @Override
  @NotNull
  public Collection<VirtualFile> getSchemaFilesForFile(@NotNull final VirtualFile file) {
    return getSchemasForFile(file, false, false);
  }

  @NotNull
  public Collection<VirtualFile> getSchemasForFile(@NotNull VirtualFile file, boolean single, boolean onlyUserSchemas) {
    String schemaUrl = null;
    if (!onlyUserSchemas) {
      // prefer schema-schema if it is specified in "$schema" property
      schemaUrl = JsonCachedValues.getSchemaUrlFromSchemaProperty(file, myProject);
      if (JsonFileResolver.isSchemaUrl(schemaUrl)) {
        final VirtualFile virtualFile = resolveFromSchemaProperty(schemaUrl, file);
        if (virtualFile != null) return Collections.singletonList(virtualFile);
      }
    }


    List<JsonSchemaFileProvider> providers = getProvidersForFile(file);

    // proper priority:
    // 1) user providers
    // 2) $schema property
    // 3) built-in providers
    // 4) schema catalog

    boolean checkSchemaProperty = true;
    if (!onlyUserSchemas && providers.stream().noneMatch(p -> p.getSchemaType() == SchemaType.userSchema)) {
      if (schemaUrl == null) schemaUrl = JsonCachedValues.getSchemaUrlFromSchemaProperty(file, myProject);
      VirtualFile virtualFile = resolveFromSchemaProperty(schemaUrl, file);
      if (virtualFile != null) return Collections.singletonList(virtualFile);
      checkSchemaProperty = false;
    }

    if (!single) {
      List<VirtualFile> files = new ArrayList<>();
      for (JsonSchemaFileProvider provider : providers) {
        VirtualFile schemaFile = getSchemaForProvider(myProject, provider);
        if (schemaFile != null) {
          files.add(schemaFile);
        }
      }
      if (!files.isEmpty()) {
        return files;
      }
    }
    else if (!providers.isEmpty()) {
      final JsonSchemaFileProvider selected;
      if (providers.size() > 2) return ContainerUtil.emptyList();
      if (providers.size() > 1) {
        final Optional<JsonSchemaFileProvider> userSchema =
          providers.stream().filter(provider -> SchemaType.userSchema.equals(provider.getSchemaType())).findFirst();
        if (!userSchema.isPresent()) return ContainerUtil.emptyList();
        selected = userSchema.get();
      } else selected = providers.get(0);
      VirtualFile schemaFile = getSchemaForProvider(myProject, selected);
      return ContainerUtil.createMaybeSingletonList(schemaFile);
    }

    if (onlyUserSchemas) {
      return ContainerUtil.emptyList();
    }

    if (checkSchemaProperty) {
      if (schemaUrl == null) schemaUrl = JsonCachedValues.getSchemaUrlFromSchemaProperty(file, myProject);
      VirtualFile virtualFile = resolveFromSchemaProperty(schemaUrl, file);
      if (virtualFile != null) return Collections.singletonList(virtualFile);
    }

    return ContainerUtil.createMaybeSingletonList(resolveSchemaFromOtherSources(file));
  }

  @NotNull
  public List<JsonSchemaFileProvider> getProvidersForFile(@NotNull VirtualFile file) {
    return ContainerUtil.filter(myState.getProviders(), provider -> isProviderAvailable(file, provider));
  }

  @Nullable
  private VirtualFile resolveFromSchemaProperty(@Nullable String schemaUrl, @NotNull VirtualFile file) {
    if (schemaUrl != null) {
      VirtualFile virtualFile = findSchemaFileByReference(schemaUrl, file);
      if (virtualFile != null) return virtualFile;
    }
    return null;
  }

  @Override
  public List<JsonSchemaInfo> getAllUserVisibleSchemas() {
    List<JsonSchemaCatalogEntry> schemas = myCatalogManager.getAllCatalogEntries();
    Collection<? extends JsonSchemaFileProvider> providers = myState.getProviders();
    List<JsonSchemaInfo> results = new ArrayList<>(schemas.size() + providers.size());
    Map<String, JsonSchemaInfo> processedRemotes = new HashMap<>();
    for (JsonSchemaFileProvider provider: providers) {
      if (provider.isUserVisible()) {
        final String remoteSource = provider.getRemoteSource();
        if (remoteSource != null) {
          if (!processedRemotes.containsKey(remoteSource)) {
            final JsonSchemaInfo info = new JsonSchemaInfo(provider);
            processedRemotes.put(remoteSource, info);
            results.add(info);
          }
        }
        else {
          results.add(new JsonSchemaInfo(provider));
        }
      }
    }

    for (JsonSchemaCatalogEntry schema: schemas) {
      final String url = schema.getUrl();
      if (!processedRemotes.containsKey(url)) {
        final JsonSchemaInfo info = new JsonSchemaInfo(url);
        if (schema.getDescription() != null) {
          info.setDocumentation(schema.getDescription());
        }
        if (schema.getName() != null) {
          info.setName(schema.getName());
        }
        results.add(info);
      }
      else {
        // use documentation from schema catalog for bundled schemas if possible
        // we don't have our own docs, so let's reuse the existing docs from the catalog
        JsonSchemaInfo info = processedRemotes.get(url);
        if (info.getDocumentation() == null) {
          info.setDocumentation(schema.getDescription());
        }
        if (info.getName() == null) {
          info.setName(schema.getName());
        }
      }
    }
    return results;
  }

  @Nullable
  @Override
  public JsonSchemaObject getSchemaObject(@NotNull final VirtualFile file) {
    Collection<VirtualFile> schemas = getSchemasForFile(file, true, false);
    if (schemas.size() == 0) return null;
    assert schemas.size() == 1;
    VirtualFile schemaFile = schemas.iterator().next();
    return JsonCachedValues.getSchemaObject(replaceHttpFileWithBuiltinIfNeeded(schemaFile), myProject);
  }


  @Nullable
  @Override
  public JsonSchemaObject getSchemaObject(@NotNull PsiFile file) {
    return JsonCachedValues.computeSchemaForFile(file, this);
  }

  public VirtualFile replaceHttpFileWithBuiltinIfNeeded(VirtualFile schemaFile) {
    // this hack is needed to handle user-defined mappings via urls
    // we cannot perform that inside corresponding provider, because it leads to recursive component dependency
    // this way we're preventing http files when a built-in schema exists
    if (schemaFile instanceof HttpVirtualFile && (!JsonSchemaCatalogProjectConfiguration.getInstance(myProject).isPreferRemoteSchemas()
                                                  || JsonFileResolver.isSchemaUrl(schemaFile.getUrl()))) {
      String url = schemaFile.getUrl();
      VirtualFile first1 = getLocalSchemaByUrl(url);
      return first1 != null ? first1 : schemaFile;
    }
    return schemaFile;
  }

  @Nullable
  public VirtualFile getLocalSchemaByUrl(String url) {
    return myState.getFiles().stream()
                  .filter(f -> {
                     JsonSchemaFileProvider prov = getSchemaProvider(f);
                     return prov != null && !(prov.getSchemaFile() instanceof HttpVirtualFile)
                            && (url.equals(prov.getRemoteSource()) || JsonFileResolver.replaceUnsafeSchemaStoreUrls(url).equals(prov.getRemoteSource())
                             || url.equals(JsonFileResolver.replaceUnsafeSchemaStoreUrls(prov.getRemoteSource())));
                  }).findFirst().orElse(null);
  }

  @Nullable
  @Override
  public JsonSchemaObject getSchemaObjectForSchemaFile(@NotNull VirtualFile schemaFile) {
    return JsonCachedValues.getSchemaObject(schemaFile, myProject);
  }

  @Override
  public boolean isSchemaFile(@NotNull VirtualFile file) {
    return isMappedSchema(file)
           || isSchemaByProvider(file)
           || hasSchemaSchema(file);
  }

  @Override
  public boolean isSchemaFile(@NotNull JsonSchemaObject schemaObject) {
    VirtualFile file = resolveSchemaFile(schemaObject);
    return file != null && isSchemaFile(file);
  }

  private boolean isMappedSchema(@NotNull VirtualFile file) {
    return isMappedSchema(file, true);
  }

  public boolean isMappedSchema(@NotNull VirtualFile file, boolean canRecompute) {
    return (canRecompute || myState.isComputed()) && myState.getFiles().contains(file);
  }

  private boolean isSchemaByProvider(@NotNull VirtualFile file) {
    JsonSchemaFileProvider provider = myState.getProvider(file);
    if (provider == null) {
      for (JsonSchemaFileProvider stateProvider: myState.getProviders()) {
        if (isSchemaProvider(stateProvider) && stateProvider.isAvailable(file))
          return true;
      }
      return false;
    }
    return isSchemaProvider(provider);
  }

  private static boolean isSchemaProvider(JsonSchemaFileProvider provider) {
    return JsonFileResolver.isSchemaUrl(provider.getRemoteSource());
  }

  @Override
  public JsonSchemaVersion getSchemaVersion(@NotNull VirtualFile file) {
    if (isMappedSchema(file)) {
      JsonSchemaFileProvider provider = myState.getProvider(file);
      if (provider != null) {
        return provider.getSchemaVersion();
      }
    }

    return getSchemaVersionFromSchemaUrl(file);
  }

  @Nullable
  private JsonSchemaVersion getSchemaVersionFromSchemaUrl(@NotNull VirtualFile file) {
    Ref<String> res = Ref.create(null);
    //noinspection CodeBlock2Expr
    ApplicationManager.getApplication().runReadAction(() -> {
      res.set(JsonCachedValues.getSchemaUrlFromSchemaProperty(file, myProject));
    });
    if (res.isNull()) return null;
    return JsonSchemaVersion.byId(res.get());
  }

  private boolean hasSchemaSchema(VirtualFile file) {
    return getSchemaVersionFromSchemaUrl(file) != null;
  }

  private static boolean isProviderAvailable(@NotNull final VirtualFile file, @NotNull JsonSchemaFileProvider provider) {
    return provider.isAvailable(file);
  }

  @Nullable
  private VirtualFile resolveSchemaFromOtherSources(@NotNull VirtualFile file) {
    return myCatalogManager.getSchemaFileForFile(file);
  }

  @Override
  public void registerRemoteUpdateCallback(Runnable callback) {
    myCatalogManager.registerCatalogUpdateCallback(callback);
  }

  @Override
  public void unregisterRemoteUpdateCallback(Runnable callback) {
    myCatalogManager.unregisterCatalogUpdateCallback(callback);
  }

  private final List<Runnable> myResetActions = ContainerUtil.createConcurrentList();

  @Override
  public void registerResetAction(Runnable action) {
    myResetActions.add(action);
  }

  @Override
  public void unregisterResetAction(Runnable action) {
    myResetActions.remove(action);
  }

  @Override
  public void registerReference(String ref) {
    int index = StringUtil.lastIndexOfAny(ref, "\\/");
    if (index >= 0) {
      ref = ref.substring(index + 1);
    }
    myRefs.add(ref);
  }

  @Override
  public boolean possiblyHasReference(String ref) {
    return myRefs.contains(ref);
  }

  @Override
  public void triggerUpdateRemote() {
    myCatalogManager.triggerUpdateCatalog(myProject);
  }

  @Override
  public boolean isApplicableToFile(@Nullable VirtualFile file) {
    if (file == null) return false;
    for (JsonSchemaEnabler e : JsonSchemaEnabler.EXTENSION_POINT_NAME.getExtensionList()) {
      if (e.isEnabledForFile(file, myProject)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public @NotNull JsonSchemaCatalogManager getCatalogManager() {
    return myCatalogManager;
  }

  private static class MyState {
    @NotNull private final Factory<List<JsonSchemaFileProvider>> myFactory;
    @NotNull private final Project myProject;
    @NotNull private final ClearableLazyValue<MultiMap<VirtualFile, JsonSchemaFileProvider>> myData;
    private final AtomicBoolean myIsComputed = new AtomicBoolean(false);

    private MyState(@NotNull final Factory<List<JsonSchemaFileProvider>> factory, @NotNull Project project) {
      myFactory = factory;
      myProject = project;
      myData = new ClearableLazyValue<MultiMap<VirtualFile, JsonSchemaFileProvider>>() {
        @NotNull
        @Override
        public MultiMap<VirtualFile, JsonSchemaFileProvider> compute() {
          MultiMap<VirtualFile, JsonSchemaFileProvider> map = createFileProviderMap(myFactory.create(), myProject);
          myIsComputed.set(true);
          return map;
        }

        @NotNull
        @Override
        public final synchronized MultiMap<VirtualFile, JsonSchemaFileProvider> getValue() {
          return super.getValue();
        }

        @Override
        public final synchronized void drop() {
          myIsComputed.set(false);
          super.drop();
        }
      };
    }

    public void reset() {
      myData.drop();
    }

    @NotNull
    public Collection<? extends JsonSchemaFileProvider> getProviders() {
      return myData.getValue().values();
    }

    @NotNull
    public Set<VirtualFile> getFiles() {
      return myData.getValue().keySet();
    }

    @Nullable
    public JsonSchemaFileProvider getProvider(@NotNull final VirtualFile file) {
      final Collection<JsonSchemaFileProvider> providers = myData.getValue().get(file);
      for (JsonSchemaFileProvider p : providers) {
        if (p.getSchemaType() == SchemaType.userSchema) {
          return p;
        }
      }
      return ContainerUtil.getFirstItem(providers);
    }

    public boolean isComputed() {
      return myIsComputed.get();
    }

    @NotNull
    private static MultiMap<VirtualFile, JsonSchemaFileProvider> createFileProviderMap(@NotNull List<JsonSchemaFileProvider> list,
                                                                                       @NotNull Project project) {
      // if there are different providers with the same schema files,
      // stream API does not allow to collect same keys with Collectors.toMap(): throws duplicate key
      final MultiMap<VirtualFile, JsonSchemaFileProvider> map = MultiMap.create();
      for (JsonSchemaFileProvider provider : list) {
        VirtualFile schemaFile;
        try {
          schemaFile = getSchemaForProvider(project, provider);
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception e) {
          LOG.error(e);
          continue;
        }

        if (schemaFile != null) {
          map.putValue(schemaFile, provider);
        }
      }
      return map;
    }
  }

  @Nullable
  private static VirtualFile getSchemaForProvider(@NotNull Project project, @NotNull JsonSchemaFileProvider provider) {
    if (JsonSchemaCatalogProjectConfiguration.getInstance(project).isPreferRemoteSchemas()) {
      final String source = provider.getRemoteSource();
      if (source != null && !source.endsWith("!") && !JsonFileResolver.isSchemaUrl(source)) {
        return VirtualFileManager.getInstance().findFileByUrl(source);
      }
    }
    return provider.getSchemaFile();
  }

  @Nullable
  @Override
  public VirtualFile resolveSchemaFile(@NotNull JsonSchemaObject schemaObject) {
    VirtualFile rawFile = schemaObject.getRawFile();
    if (rawFile != null) {
      return rawFile;
    }

    String fileUrl = schemaObject.getFileUrl();
    if (fileUrl == null) {
      return null;
    }

    return VirtualFileManager.getInstance().findFileByUrl(fileUrl);
  }
}
