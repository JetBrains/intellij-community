// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
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
import java.util.function.Consumer;
import java.util.function.Supplier;

public class JsonSchemaServiceImpl implements JsonSchemaService, ModificationTracker, Disposable {
  private static final Logger LOG = Logger.getInstance(JsonSchemaServiceImpl.class);

  @NotNull private final Project myProject;
  @NotNull private final MyState myState;
  @NotNull private final ClearableLazyValue<Set<String>> myBuiltInSchemaIds;
  @NotNull private final Set<String> myRefs = ContainerUtil.newConcurrentSet();
  private final AtomicLong myAnyChangeCount = new AtomicLong(0);

  @NotNull private final JsonSchemaCatalogManager myCatalogManager;
  @NotNull private final JsonSchemaVfsListener.JsonSchemaUpdater mySchemaUpdater;
  private final JsonSchemaProviderFactories myFactories;

  public JsonSchemaServiceImpl(@NotNull Project project) {
    myProject = project;
    myFactories = new JsonSchemaProviderFactories();
    myState = new MyState(() -> myFactories.getProviders(), myProject);
    myBuiltInSchemaIds = new ClearableLazyValue<>() {
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
    mySchemaUpdater = JsonSchemaVfsListener.startListening(project, this, connection);
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
    myFactories.reset();
    resetWithCurrentFactories();
  }

  private void resetWithCurrentFactories() {
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

  @Nullable
  public VirtualFile getDynamicSchemaForFile(@NotNull PsiFile psiFile) {
    return ContentAwareJsonSchemaFileProvider.EP_NAME.extensions()
      .map(provider -> provider.getSchemaFile(psiFile))
      .filter(schemaFile -> schemaFile != null)
      .findFirst()
      .orElse(null);
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
        if (userSchema.isEmpty()) return ContainerUtil.emptyList();
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
    Map<VirtualFile, List<JsonSchemaFileProvider>> map = myState.myData.getValue();
    if (map.isEmpty()) {
      return Collections.emptyList();
    }

    List<JsonSchemaFileProvider> result = null;
    for (List<JsonSchemaFileProvider> providers : map.values()) {
      for (JsonSchemaFileProvider provider : providers) {
        if (isProviderAvailable(file, provider)) {
          if (result == null) {
            result = new SmartList<>();
          }
          result.add(provider);
        }
      }
    }
    return result == null ? Collections.emptyList() : result;
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
    Map<VirtualFile, List<JsonSchemaFileProvider>> map = myState.myData.getValue();
    List<JsonSchemaInfo> results = new ArrayList<>(schemas.size() + map.size());
    Map<String, JsonSchemaInfo> processedRemotes = new HashMap<>();
    myState.processProviders(provider -> {
      if (provider.isUserVisible()) {
        final String remoteSource = provider.getRemoteSource();
        if (remoteSource != null) {
          if (!processedRemotes.containsKey(remoteSource)) {
            JsonSchemaInfo info = new JsonSchemaInfo(provider);
            processedRemotes.put(remoteSource, info);
            results.add(info);
          }
        }
        else {
          results.add(new JsonSchemaInfo(provider));
        }
      }
    });

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
    if (provider != null) {
      return isSchemaProvider(provider);
    }

    Map<VirtualFile, List<JsonSchemaFileProvider>> map = myState.myData.getValue();
    for (List<JsonSchemaFileProvider> providers : map.values()) {
      for (JsonSchemaFileProvider p : providers) {
        if (isSchemaProvider(p) && p.isAvailable(file)) {
          return true;
        }
      }
    }
    return false;
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
  public void registerRemoteUpdateCallback(@NotNull Runnable callback) {
    myCatalogManager.registerCatalogUpdateCallback(callback);
  }

  @Override
  public void unregisterRemoteUpdateCallback(@NotNull Runnable callback) {
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

  private static final class MyState {
    @NotNull private final Supplier<List<JsonSchemaFileProvider>> myFactory;
    @NotNull private final Project myProject;
    @NotNull private final ClearableLazyValue<Map<VirtualFile, List<JsonSchemaFileProvider>>> myData;
    private final AtomicBoolean myIsComputed = new AtomicBoolean(false);

    private MyState(@NotNull final Supplier<List<JsonSchemaFileProvider>> factory, @NotNull Project project) {
      myFactory = factory;
      myProject = project;
      myData = new ClearableLazyValue<>() {
        @NotNull
        @Override
        public Map<VirtualFile, List<JsonSchemaFileProvider>> compute() {
          Map<VirtualFile, List<JsonSchemaFileProvider>> map = createFileProviderMap(myFactory.get(), myProject);
          myIsComputed.set(true);
          return map;
        }

        @NotNull
        @Override
        public synchronized Map<VirtualFile, List<JsonSchemaFileProvider>> getValue() {
          return super.getValue();
        }

        @Override
        public synchronized void drop() {
          myIsComputed.set(false);
          super.drop();
        }
      };
    }

    public void reset() {
      myData.drop();
    }

    public void processProviders(@NotNull Consumer<JsonSchemaFileProvider> consumer) {
      Map<VirtualFile, List<JsonSchemaFileProvider>> map = myData.getValue();
      if (map.isEmpty()) {
        return;
      }

      for (List<JsonSchemaFileProvider> providers : map.values()) {
        providers.forEach(consumer);
      }
    }

    @NotNull
    public Set<VirtualFile> getFiles() {
      return myData.getValue().keySet();
    }

    @Nullable
    public JsonSchemaFileProvider getProvider(@NotNull VirtualFile file) {
      List<JsonSchemaFileProvider> providers = myData.getValue().get(file);
      if (providers == null || providers.isEmpty()) {
        return null;
      }

      for (JsonSchemaFileProvider p : providers) {
        if (p.getSchemaType() == SchemaType.userSchema) {
          return p;
        }
      }
      return providers.get(0);
    }

    public boolean isComputed() {
      return myIsComputed.get();
    }

    @NotNull
    private static Map<VirtualFile, List<JsonSchemaFileProvider>> createFileProviderMap(@NotNull List<JsonSchemaFileProvider> list,
                                                                                        @NotNull Project project) {
      // if there are different providers with the same schema files,
      // stream API does not allow to collect same keys with Collectors.toMap(): throws duplicate key
      Map<VirtualFile, List<JsonSchemaFileProvider>> map = new HashMap<>();
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
          map.computeIfAbsent(schemaFile, __ -> new SmartList<>()).add(provider);
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

  private class JsonSchemaProviderFactories {

    private volatile List<JsonSchemaFileProvider> myProviders;

    public @NotNull List<JsonSchemaFileProvider> getProviders() {
      List<JsonSchemaFileProvider> providers = myProviders;
      if (providers == null) {
        providers = getDumbAwareProvidersAndUpdateRestWhenSmart();
        myProviders = providers;
      }
      return providers;
    }

    public void reset() {
      myProviders = null;
    }

    private @NotNull List<JsonSchemaFileProvider> getDumbAwareProvidersAndUpdateRestWhenSmart() {
      List<JsonSchemaProviderFactory> readyFactories = new ArrayList<>();
      List<JsonSchemaProviderFactory> notReadyFactories = new ArrayList<>();
      boolean dumb = DumbService.getInstance(myProject).isDumb();
      for (JsonSchemaProviderFactory factory : getProviderFactories()) {
        if (!dumb || DumbService.isDumbAware(factory)) {
          readyFactories.add(factory);
        }
        else {
          notReadyFactories.add(factory);
        }
      }
      List<JsonSchemaFileProvider> providers = getProvidersFromFactories(readyFactories);
      myProviders = providers;
      if (!notReadyFactories.isEmpty() && !LightEdit.owns(myProject)) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          if (myProject.isDisposed()) return;
          DumbService.getInstance(myProject).runReadActionInSmartMode(() -> {
            if (myProviders == providers) {
              List<JsonSchemaFileProvider> newProviders = getProvidersFromFactories(notReadyFactories);
              if (!newProviders.isEmpty()) {
                List<JsonSchemaFileProvider> oldProviders = myProviders;
                myProviders = ContainerUtil.concat(oldProviders, newProviders);
                JsonSchemaServiceImpl.this.resetWithCurrentFactories();
              }
            }
          });
        });
      }
      return providers;
    }

    private @NotNull List<JsonSchemaFileProvider> getProvidersFromFactories(@NotNull List<JsonSchemaProviderFactory> factories) {
      List<JsonSchemaFileProvider> providers = new ArrayList<>();
      for (JsonSchemaProviderFactory factory : factories) {
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
  }
}
