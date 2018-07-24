// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicClearableLazyValue;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.util.containers.ConcurrentList;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.JsonSchemaVfsListener;
import com.jetbrains.jsonSchema.extension.*;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.remote.JsonFileResolver;
import com.jetbrains.jsonSchema.remote.JsonSchemaCatalogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class JsonSchemaServiceImpl implements JsonSchemaService {
  @NotNull private final Project myProject;
  @NotNull private final MyState myState;
  private final AtomicLong myAnyChangeCount = new AtomicLong(0);
  private final ModificationTracker myAnySchemaChangeTracker;

  @NotNull private final JsonSchemaCatalogManager myCatalogManager;

  public JsonSchemaServiceImpl(@NotNull Project project) {
    myProject = project;
    myState = new MyState(() -> getProvidersFromFactories());
    myAnySchemaChangeTracker = () -> myAnyChangeCount.get();
    myCatalogManager = new JsonSchemaCatalogManager(myProject);

    project.getMessageBus().connect().subscribe(JsonSchemaVfsListener.JSON_SCHEMA_CHANGED, myAnyChangeCount::incrementAndGet);
    JsonSchemaVfsListener.startListening(project, this);
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
    return optional.orElseGet(() -> JsonFileResolver.resolveSchemaByReference(referent, JsonSchemaService.normalizeId(reference)));
  }

  private Optional<VirtualFile> findBuiltInSchemaByReference(@NotNull String reference) {
    return myState.getFiles().stream()
        .filter(file -> JsonSchemaService.normalizeId(reference).equals(JsonCachedValues.getSchemaId(file, myProject)))
        .findFirst();
  }

  @Override
  @NotNull
  public Collection<VirtualFile> getSchemaFilesForFile(@NotNull final VirtualFile file) {
    return getSchemasForFile(file, false);
  }

  @NotNull
  private Collection<VirtualFile> getSchemasForFile(@NotNull VirtualFile file, boolean single) {
    List<JsonSchemaFileProvider> providers = getProvidersForFile(file);

    // proper priority:
    // 1) user providers
    // 2) $schema property
    // 3) built-in providers
    // 4) schema catalog

    boolean checkSchemaProperty = true;
    if (providers.stream().noneMatch(p -> p.getSchemaType() == SchemaType.userSchema)) {
      VirtualFile virtualFile = resolveFromSchemaProperty(file);
      if (virtualFile != null) return ContainerUtil.createMaybeSingletonList(virtualFile);
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

    if (checkSchemaProperty) {
      VirtualFile virtualFile = resolveFromSchemaProperty(file);
      if (virtualFile != null) return ContainerUtil.createMaybeSingletonList(virtualFile);
    }

    return ContainerUtil.createMaybeSingletonList(resolveSchemaFromOtherSources(file));
  }

  @NotNull
  private List<JsonSchemaFileProvider> getProvidersForFile(@NotNull VirtualFile file) {
    return myState.getProviders().stream().filter(provider -> isProviderAvailable(file, provider)).collect(
      Collectors.toList());
  }

  @Nullable
  private VirtualFile resolveFromSchemaProperty(@NotNull VirtualFile file) {
    String schemaUrl = JsonCachedValues.getSchemaUrlFromSchemaProperty(file, myProject);

    if (schemaUrl != null) {
      VirtualFile virtualFile = findSchemaFileByReference(schemaUrl, file);
      if (virtualFile != null) return virtualFile;
    }
    return null;
  }

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
    Collection<VirtualFile> schemas = getSchemasForFile(file, true);
    if (schemas.size() == 0) return null;
    assert schemas.size() == 1;
    VirtualFile schemaFile = schemas.iterator().next();
    return JsonCachedValues.getSchemaObject(replaceHttpFileWithBuiltinIfNeeded(schemaFile), myProject);
  }

  public VirtualFile replaceHttpFileWithBuiltinIfNeeded(VirtualFile schemaFile) {
    // this hack is needed to handle user-defined mappings via urls
    // we cannot perform that inside corresponding provider, because it leads to recursive component dependency
    // this way we're preventing http files when a built-in schema exists
    if (schemaFile instanceof HttpVirtualFile) {
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
    return myState.getFiles().contains(file)
           || isSchemaByProvider(file)
           || hasSchemaSchema(file);
  }

  private boolean isSchemaByProvider(@NotNull VirtualFile file) {
    JsonSchemaFileProvider provider = myState.getProvider(file);
    if (provider == null) return false;
    VirtualFile schemaFile = provider.getSchemaFile();
    if (schemaFile == null) return false;
    String url = schemaFile.getUrl();
    return url.startsWith("http://json-schema.org/") && url.endsWith("/schema");
  }

  @Override
  public JsonSchemaVersion getSchemaVersion(@NotNull VirtualFile file) {
    if (myState.getFiles().contains(file)) {
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
    final FileType type = file.getFileType();
    final boolean isJson = type instanceof LanguageFileType && ((LanguageFileType)type).getLanguage().isKindOf(JsonLanguage.INSTANCE);
    return (isJson || !SchemaType.userSchema.equals(provider.getSchemaType())) && provider.isAvailable(file);
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
  public void triggerUpdateRemote() {
    myCatalogManager.triggerUpdateCatalog(myProject);
  }

  @Override
  public boolean isApplicableToFile(@Nullable VirtualFile file) {
    if (file == null) return false;
    return Arrays.stream(JsonSchemaEnabler.EXTENSION_POINT_NAME.getExtensions()).anyMatch(e -> e.isEnabledForFile(file));
  }

  private static class MyState {
    @NotNull private final Factory<List<JsonSchemaFileProvider>> myFactory;
    @NotNull private final AtomicClearableLazyValue<Map<VirtualFile, JsonSchemaFileProvider>> myData;

    private MyState(@NotNull final Factory<List<JsonSchemaFileProvider>> factory) {
      myFactory = factory;
      myData = new AtomicClearableLazyValue<Map<VirtualFile, JsonSchemaFileProvider>>() {
        @NotNull
        @Override
        public Map<VirtualFile, JsonSchemaFileProvider> compute() {
          return Collections.unmodifiableMap(createFileProviderMap(myFactory.create()));
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

    private static Map<VirtualFile, JsonSchemaFileProvider> createFileProviderMap(@NotNull final List<JsonSchemaFileProvider> list) {
      // if there are different providers with the same schema files,
      // stream API does not allow to collect same keys with Collectors.toMap(): throws duplicate key
      final Map<VirtualFile, JsonSchemaFileProvider> map = new HashMap<>();
      list.stream().filter(provider -> provider.getSchemaFile() != null)
        .forEach(provider -> map.put(provider.getSchemaFile(), provider));
      return map;
    }
  }
}
