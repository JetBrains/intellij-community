// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.json.JsonUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.util.containers.ConcurrentList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.jsonSchema.JsonSchemaCatalogProjectConfiguration;
import com.jetbrains.jsonSchema.JsonSchemaVfsListener;
import com.jetbrains.jsonSchema.extension.*;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.remote.JsonFileResolver;
import com.jetbrains.jsonSchema.remote.JsonSchemaCatalogManager;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class JsonSchemaServiceImpl implements JsonSchemaService {
  @NotNull private final Project myProject;
  @NotNull private final MyState myState;
  @NotNull private final ClearableLazyValue<Set<String>> myBuiltInSchemaIds;
  @NotNull private final Set<String> myRefs = ContainerUtil.newConcurrentSet();
  private final AtomicLong myAnyChangeCount = new AtomicLong(0);
  private final ModificationTracker myAnySchemaChangeTracker;

  @NotNull private final JsonSchemaCatalogManager myCatalogManager;

  public JsonSchemaServiceImpl(@NotNull Project project) {
    myProject = project;
    myState = new MyState(() -> getProvidersFromFactories(), myProject);
    myBuiltInSchemaIds = new ClearableLazyValue<Set<String>>() {
      @NotNull
      @Override
      protected Set<String> compute() {
        return myState.getFiles().stream().map(f -> JsonCachedValues.getSchemaId(f, myProject)).collect(Collectors.toSet());
      }
    };
    myAnySchemaChangeTracker = () -> myAnyChangeCount.get();
    myCatalogManager = new JsonSchemaCatalogManager(myProject);

    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(JsonSchemaVfsListener.JSON_SCHEMA_CHANGED, myAnyChangeCount::incrementAndGet);
    connection.subscribe(JsonSchemaVfsListener.JSON_DEPS_CHANGED, () -> {
      myRefs.clear();
      myAnyChangeCount.incrementAndGet();
    });
    JsonSchemaVfsListener.startListening(project, this, connection);
    myCatalogManager.startUpdates();
  }

  @Override
  public ModificationTracker getAnySchemaChangeTracker() {
    return myAnySchemaChangeTracker;
  }

  private List<JsonSchemaFileProvider> getProvidersFromFactories() {
    List<JsonSchemaFileProvider> providers = new ArrayList<>();
    for (JsonSchemaProviderFactory factory : getProviderFactories()) {
      try {
        providers.addAll(factory.getProviders(myProject));
      }
      catch (Exception e) {
        Logger.getInstance(JsonSchemaService.class).error(e);
      }
    }
    return providers;
  }

  @NotNull
  protected JsonSchemaProviderFactory[] getProviderFactories() {
    return JsonSchemaProviderFactory.EP_NAME.getExtensions();
  }

  @Nullable
  @Override
  public JsonSchemaFileProvider getSchemaProvider(@NotNull VirtualFile schemaFile) {
    return myState.getProvider(schemaFile);
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
  @Nullable
  public VirtualFile findSchemaFileByReference(@NotNull String reference, @Nullable VirtualFile referent) {
    final Optional<VirtualFile> optional = findBuiltInSchemaByReference(reference);
    return optional.orElseGet(() -> {
      if (reference.startsWith("#")) return referent;
      return JsonFileResolver.resolveSchemaByReference(referent, JsonSchemaService.normalizeId(reference));
    });
  }

  private Optional<VirtualFile> findBuiltInSchemaByReference(@NotNull String reference) {
    String id = JsonSchemaService.normalizeId(reference);
    if (!myBuiltInSchemaIds.getValue().contains(id)) return Optional.empty();
    return myState.getFiles().stream()
        .filter(file -> id.equals(JsonCachedValues.getSchemaId(file, myProject)))
        .findFirst();
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
      if (isSchemaUrl(schemaUrl)) {
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
      List<VirtualFile> files = ContainerUtil.newArrayList();
      for (JsonSchemaFileProvider provider : providers) {
        VirtualFile schemaFile = provider.getSchemaFile();
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
      VirtualFile schemaFile = selected.getSchemaFile();
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
    List<String> schemas = myCatalogManager.getAllCatalogSchemas();
    Collection<JsonSchemaFileProvider> providers = myState.getProviders();
    List<JsonSchemaInfo> results = ContainerUtil.newArrayListWithCapacity(schemas.size() + providers.size());
    Set<String> processedRemotes = ContainerUtil.newHashSet();
    for (JsonSchemaFileProvider provider: providers) {
      if (provider.isUserVisible()) {
        if (provider.getRemoteSource() != null) {
          if (processedRemotes.add(provider.getRemoteSource())) {
            results.add(new JsonSchemaInfo(provider));
          }
        }
        else {
          results.add(new JsonSchemaInfo(provider));
        }
      }
    }

    for (String schema: schemas) {
      if (processedRemotes.add(schema)) {
        results.add(new JsonSchemaInfo(schema));
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

  public VirtualFile replaceHttpFileWithBuiltinIfNeeded(VirtualFile schemaFile) {
    // this hack is needed to handle user-defined mappings via urls
    // we cannot perform that inside corresponding provider, because it leads to recursive component dependency
    // this way we're preventing http files when a built-in schema exists
    if (!JsonSchemaCatalogProjectConfiguration.getInstance(myProject).isPreferRemoteSchemas()
        && schemaFile instanceof HttpVirtualFile) {
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
    return JsonUtil.isJsonFile(file) && (isMappedSchema(file)
                                         || isSchemaByProvider(file)
                                         || hasSchemaSchema(file));
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
    VirtualFile schemaFile = provider.getSchemaFile();
    if (!(schemaFile instanceof HttpVirtualFile)) return false;
    String url = schemaFile.getUrl();
    return isSchemaUrl(url);
  }

  private static boolean isSchemaUrl(@Nullable String url) {
    return url != null && url.startsWith("http://json-schema.org/") && url.endsWith("/schema");
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

  private final ConcurrentList<Runnable> myResetActions = ContainerUtil.createConcurrentList();

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
      if (e.isEnabledForFile(file)) {
        return true;
      }
    }
    return false;
  }

  private static class MyState {
    @NotNull private final Factory<List<JsonSchemaFileProvider>> myFactory;
    @NotNull private final Project myProject;
    @NotNull private final ClearableLazyValue<Map<VirtualFile, JsonSchemaFileProvider>> myData;
    private final AtomicBoolean myIsComputed = new AtomicBoolean(false);

    private MyState(@NotNull final Factory<List<JsonSchemaFileProvider>> factory, @NotNull Project project) {
      myFactory = factory;
      myProject = project;
      myData = new ClearableLazyValue<Map<VirtualFile, JsonSchemaFileProvider>>() {
        @NotNull
        @Override
        public Map<VirtualFile, JsonSchemaFileProvider> compute() {
          myIsComputed.set(true);
          return Collections.unmodifiableMap(createFileProviderMap(myFactory.create(), myProject));
        }

        @NotNull
        @Override
        public final synchronized Map<VirtualFile, JsonSchemaFileProvider> getValue() {
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
    public Collection<JsonSchemaFileProvider> getProviders() {
      return myData.getValue().values();
    }

    @NotNull
    public Set<VirtualFile> getFiles() {
      return myData.getValue().keySet();
    }

    @Nullable
    public JsonSchemaFileProvider getProvider(@NotNull final VirtualFile file) {
      return myData.getValue().get(file);
    }

    public boolean isComputed() {
      return myIsComputed.get();
    }

    @NotNull
    private static Map<VirtualFile, JsonSchemaFileProvider> createFileProviderMap(@NotNull final List<JsonSchemaFileProvider> list,
                                                                                  @NotNull Project project) {
      // if there are different providers with the same schema files,
      // stream API does not allow to collect same keys with Collectors.toMap(): throws duplicate key
      final Map<VirtualFile, JsonSchemaFileProvider> map = new THashMap<>();
      for (JsonSchemaFileProvider provider : list) {
        VirtualFile schemaFile = null;
        if (JsonSchemaCatalogProjectConfiguration.getInstance(project).isPreferRemoteSchemas()) {
          final String source = provider.getRemoteSource();
          if (source != null && !source.endsWith("!")) {
            schemaFile = VirtualFileManager.getInstance().findFileByUrl(source);
          }
        }
        if (schemaFile == null) {
          schemaFile = provider.getSchemaFile();
        }
        if (schemaFile != null) {
          map.put(schemaFile, provider);
        }
      }
      return map;
    }
  }
}
