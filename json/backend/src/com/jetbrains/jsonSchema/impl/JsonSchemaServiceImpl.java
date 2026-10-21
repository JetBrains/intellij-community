// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.TrustedFiles;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.ide.trustedProjects.TrustedProjectsListener;
import com.intellij.ide.trustedProjects.TrustedProjectsLocator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.jsonSchema.JsonPointerUtil;
import com.jetbrains.jsonSchema.JsonSchemaCatalogEntry;
import com.jetbrains.jsonSchema.JsonSchemaCatalogProjectConfiguration;
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration;
import com.jetbrains.jsonSchema.JsonSchemaVfsListener;
import com.jetbrains.jsonSchema.extension.ContentAwareJsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import com.jetbrains.jsonSchema.extension.SchemaType;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectStorage;
import com.jetbrains.jsonSchema.remote.JsonFileResolver;
import com.jetbrains.jsonSchema.remote.JsonSchemaCatalogExclusion;
import com.jetbrains.jsonSchema.remote.JsonSchemaCatalogManager;
import com.jetbrains.jsonSchema.remote.http.JsonSchemaRemoteContentService;
import com.jetbrains.jsonSchema.remote.http.SchemaOrigin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class JsonSchemaServiceImpl implements JsonSchemaService, ModificationTracker, Disposable {
  private static final Logger LOG = Logger.getInstance(JsonSchemaServiceImpl.class);

  private final @NotNull Project myProject;
  private final @NotNull MyState myState;
  private final @NotNull ClearableLazyValue<@Unmodifiable Set<String>> myBuiltInSchemaIds;
  private final @NotNull Set<String> myRefs = ConcurrentCollectionFactory.createConcurrentSet();
  private final AtomicLong myAnyChangeCount = new AtomicLong(0);

  private final @NotNull JsonSchemaCatalogManager myCatalogManager;
  private final JsonSchemaProviderFactories myFactories;

  public JsonSchemaServiceImpl(@NotNull Project project) {
    myProject = project;
    myFactories = new JsonSchemaProviderFactories();
    myState = new MyState(() -> myFactories.getProviders());
    myBuiltInSchemaIds = new ClearableLazyValue<>() {
      @Override
      protected @NotNull Set<String> compute() {
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
    ApplicationManager.getApplication().getMessageBus().connect(this)
      .subscribe(TrustedProjectsListener.TOPIC, new TrustedProjectsListener() {
        @Override
        public void onProjectTrusted(@NotNull TrustedProjectsLocator.LocatedProject locatedProject) {
          resetAfterTrustChange(locatedProject);
        }

        @Override
        public void onProjectUntrusted(@NotNull TrustedProjectsLocator.LocatedProject locatedProject) {
          resetAfterTrustChange(locatedProject);
        }
      });
    JsonSchemaVfsListener.startListening(project);
    myCatalogManager.startUpdates(this);
  }

  private void resetAfterTrustChange(@NotNull TrustedProjectsLocator.LocatedProject locatedProject) {
    // a per-file trust grant fires a path-only event with a null project; it can affect this project's files
    Project project = locatedProject.getProject();
    if (project == null || project == myProject) {
      reset();
    }
  }

  @Override
  public long getModificationCount() {
    return myAnyChangeCount.get();
  }

  @Override
  public void dispose() {
  }

  protected @NotNull List<JsonSchemaProviderFactory> getProviderFactories() {
    return JsonSchemaProviderFactory.EP_NAME.getExtensionList();
  }

  @Override
  public @Nullable JsonSchemaFileProvider getSchemaProvider(@NotNull VirtualFile schemaFile) {
    return myState.getProvider(schemaFile);
  }

  @Override
  public @Nullable JsonSchemaFileProvider getSchemaProvider(@NotNull JsonSchemaObject schemaObject) {
    VirtualFile file = resolveSchemaFile(schemaObject);
    return file == null ? null : getSchemaProvider(file);
  }

  @Override
  public @Nullable JsonSchemaFileProvider getSchemaProviderForFile(@NotNull VirtualFile file, @NotNull JsonSchemaObject schemaObject) {
    VirtualFile schemaFile = resolveSchemaFile(schemaObject);
    return schemaFile == null ? null : myState.getProvider(schemaFile, file);
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
    for (Runnable action : myResetActions) {
      action.run();
    }

    if (!myProject.isDisposed()) {
      DaemonCodeAnalyzer daemonCodeAnalyzer = myProject.getServiceIfCreated(DaemonCodeAnalyzer.class);
      if (daemonCodeAnalyzer != null) {
        daemonCodeAnalyzer.restart(this);
      }
    }
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public @Nullable VirtualFile findSchemaFileByReference(@NotNull String reference, @NotNull VirtualFile referent) {
    boolean restrictReference = shouldRestrictSchemaReferences(referent);
    final VirtualFile file = restrictReference
                             ? findAllowedRegisteredSchemaByReference(reference)
                             : findBuiltInSchemaByReference(reference);
    if (file != null) return file;
    if (reference.startsWith("#")) return referent;
    String normalizedReference = JsonPointerUtil.normalizeId(reference);
    VirtualFile resolved;
    if (restrictReference) {
      String resolvedUrl = JsonFileResolver.resolveSchemaUrlByReference(referent, normalizedReference);
      if (resolvedUrl == null || isForbiddenBeforeResolution(resolvedUrl)) return null;
      resolved = JsonFileResolver.urlToFile(resolvedUrl);
    }
    else {
      resolved = JsonFileResolver.resolveSchemaByReference(referent, normalizedReference, myProject);
    }

    if (restrictReference && resolved != null && isForbiddenAfterResolution(resolved)) {
      return null;
    }
    return resolved;
  }

  /**
   * Restricts {@code $schema}/{@code $ref} references originating in a project before the user has granted trust
   * (IJPL-249802, CWE-22 / CWE-862 / CWE-918).
   * Also restricts references from a file in the safe mode (IJPL-254130).
   */
  @Override
  public boolean shouldRestrictSchemaReferences(@NotNull VirtualFile referent) {
    if (!TrustedFiles.isTrusted(referent, myProject)) return true;
    if (TrustedProjects.isProjectTrusted(myProject)) return false;
    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(myProject);
    return fileIndex.isInContent(referent) || isPathInsideProject(toPath(referent.getPath(), false));
  }

  private boolean isForbiddenBeforeResolution(@NotNull String resolvedUrl) {
    if (!LocalFileSystem.PROTOCOL.equals(VirtualFileManager.extractProtocol(resolvedUrl))) return true;
    return !isPathInsideProject(toPath(VirtualFileManager.extractPath(resolvedUrl), false));
  }

  private boolean isForbiddenAfterResolution(@NotNull VirtualFile resolved) {
    if (!resolved.isInLocalFileSystem()) return true;
    try {
      return !isPathInsideProject(resolved.toNioPath().toRealPath());
    }
    catch (IOException | UnsupportedOperationException e) {
      return true;
    }
  }

  private boolean isPathInsideProject(@Nullable Path path) {
    if (path == null) return false;
    String basePath = myProject.getBasePath();
    return basePath != null && startsWithProjectRoot(path, basePath);
  }

  private static boolean startsWithProjectRoot(@NotNull Path path, @NotNull String rootPath) {
    Path normalizedRoot = toPath(rootPath, false);
    if (normalizedRoot != null && path.startsWith(normalizedRoot)) return true;
    Path realRoot = toPath(rootPath, true);
    return realRoot != null && path.startsWith(realRoot);
  }

  private static @Nullable Path toPath(@NotNull String path, boolean realPath) {
    try {
      Path nioPath = Path.of(path);
      return realPath ? nioPath.toRealPath() : nioPath.toAbsolutePath().normalize();
    }
    catch (IOException | InvalidPathException e) {
      return null;
    }
  }

  @Override
  public @Nullable VirtualFile findBuiltInSchemaByReference(@NotNull String reference) {
    String id = JsonPointerUtil.normalizeId(reference);
    if (!myBuiltInSchemaIds.getValue().contains(id)) return null;
    for (VirtualFile file : myState.getFiles()) {
      if (id.equals(JsonCachedValues.getSchemaId(file, myProject))) {
        return file;
      }
    }
    return null;
  }

  private @Nullable VirtualFile findAllowedRegisteredSchemaByReference(@NotNull String reference) {
    String id = JsonPointerUtil.normalizeId(reference);
    for (JsonSchemaFileProvider provider : myFactories.getProviders()) {
      VirtualFile file;
      try {
        file = provider.getSchemaFile();
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
        continue;
      }
      if (file == null || (!isBundledSchemaProvider(provider) && isForbiddenAfterResolution(file))) continue;
      if (id.equals(JsonCachedValues.getSchemaId(file, myProject))) {
        return file;
      }
    }
    return null;
  }

  private static boolean isBundledSchemaProvider(@NotNull JsonSchemaFileProvider provider) {
    return provider.getSchemaType() == SchemaType.schema || provider.getSchemaType() == SchemaType.embeddedSchema;
  }

  @Override
  public @NotNull Collection<VirtualFile> getSchemaFilesForFile(final @NotNull VirtualFile file) {
    return getSchemasForFile(file, false, false);
  }

  @Override
  public @Nullable VirtualFile getDynamicSchemaForFile(@NotNull PsiFile psiFile) {
    return ContentAwareJsonSchemaFileProvider.EP_NAME.getExtensionList().stream()
      .map(provider -> provider.getSchemaFile(psiFile))
      .filter(schemaFile -> schemaFile != null)
      .findFirst()
      .orElse(null);
  }

  public @NotNull Collection<VirtualFile> getSchemasForFile(@NotNull VirtualFile file, boolean single, boolean onlyUserSchemas) {
    if (JsonSchemaMappingsProjectConfiguration.getInstance(myProject).isIgnoredFile(file)) return Collections.emptyList();
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
    // SchemaStore matches are suggested separately via editor notification.

    boolean checkSchemaProperty = true;
    if (!onlyUserSchemas && providers.stream().noneMatch(p -> p.getSchemaType() == SchemaType.userSchema)) {
      if (schemaUrl == null) schemaUrl = JsonCachedValues.getSchemaUrlFromSchemaProperty(file, myProject);
      if (schemaUrl == null) schemaUrl = JsonSchemaByCommentProvider.getCommentSchema(file, myProject);
      VirtualFile virtualFile = resolveFromSchemaProperty(schemaUrl, file);
      if (virtualFile != null) return Collections.singletonList(virtualFile);
      checkSchemaProperty = false;
    }

    if (!single) {
      List<VirtualFile> files = new ArrayList<>();
      for (JsonSchemaFileProvider provider : providers) {
        VirtualFile schemaFile = getAllowedSchemaForProvider(file, provider);
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
      if (providers.size() > 1) {
        final Optional<JsonSchemaFileProvider> userSchema =
          providers.stream().filter(provider -> SchemaType.userSchema.equals(provider.getSchemaType())).findFirst();
        selected = userSchema.orElse(providers.getFirst());
      }
      else {
        selected = providers.getFirst();
      }
      VirtualFile schemaFile = getAllowedSchemaForProvider(file, selected);
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

    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    if (psiFile == null) {
      return Collections.emptyList();
    }
    else {
      return ContainerUtil.createMaybeSingletonList(getDynamicSchemaForFile(psiFile));
    }
  }

  public @NotNull List<JsonSchemaFileProvider> getProvidersForFile(@NotNull VirtualFile file) {
    List<JsonSchemaFileProvider> providers = myState.getProviders();
    if (providers.isEmpty()) {
      return Collections.emptyList();
    }

    List<JsonSchemaFileProvider> result = null;
    for (JsonSchemaFileProvider provider : providers) {
      if (isProviderAvailable(file, provider)) {
        if (result == null) {
          result = new SmartList<>();
        }
        result.add(provider);
      }
    }
    return result == null ? Collections.emptyList() : result;
  }

  private @Nullable VirtualFile resolveFromSchemaProperty(@Nullable String schemaUrl, @NotNull VirtualFile file) {
    if (schemaUrl != null) {
      VirtualFile virtualFile = findSchemaFileByReference(schemaUrl, file);
      if (virtualFile != null) return virtualFile;
    }
    return null;
  }

  @Override
  public List<JsonSchemaInfo> getAllUserVisibleSchemas() {
    List<JsonSchemaCatalogEntry> schemas = myCatalogManager.getAllCatalogEntries();
    List<JsonSchemaFileProvider> providers = myState.getProviders();
    List<JsonSchemaInfo> results = new ArrayList<>(schemas.size() + providers.size());
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

    for (JsonSchemaCatalogEntry schema : schemas) {
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

  @Override
  public @Nullable JsonSchemaObject getSchemaObject(final @NotNull VirtualFile file) {
    Collection<VirtualFile> schemas = getSchemasForFile(file, true, false);
    if (schemas.isEmpty()) return null;
    assert schemas.size() == 1;
    VirtualFile schemaFile = schemas.iterator().next();
    JsonSchemaObject result = JsonCachedValues.getSchemaObject(schemaFile, myProject);
    if (result == null) {
      String message = "JSON Schema mapped to '" + file.getName() + "' could not be loaded from '" + schemaFile.getUrl() +
                       "' (valid=" + schemaFile.isValid() + ", length=" + schemaFile.getLength() + ")";
      LOG.warn(message);
    }
    return result;
  }


  @Override
  public @Nullable JsonSchemaObject getSchemaObject(@NotNull PsiFile file) {
    return JsonCachedValues.computeSchemaForFile(file, this);
  }

  @Override
  public @Nullable JsonSchemaObject getSchemaObjectForSchemaFile(@NotNull VirtualFile schemaFile) {
    return JsonCachedValues.getSchemaObject(schemaFile, myProject);
  }

  @Override
  public boolean isSchemaFile(@NotNull VirtualFile file) {
    return !file.isDirectory()
           && (isMappedSchema(file)
               || isSchemaByProvider(file)
               || hasSchemaSchema(file));
  }

  @Override
  public boolean isSchemaFile(@NotNull JsonSchemaObject schemaObject) {
    VirtualFile file = resolveSchemaFile(schemaObject);
    return file != null && isSchemaFile(file);
  }

  public boolean isMappedSchema(@NotNull VirtualFile file) {
    return isMappedSchema(file, true);
  }

  public boolean isMappedSchema(@NotNull VirtualFile file, boolean canRecompute) {
    if (!canRecompute) {
      return myState.isMappedIfComputed(file);
    }
    if (myState.getFiles().contains(file)) {
      return true;
    }
    return myState.getProvider(file) != null;
  }

  private boolean isSchemaByProvider(@NotNull VirtualFile file) {
    JsonSchemaFileProvider provider = myState.getProvider(file);
    if (provider != null) {
      return isSchemaProvider(provider);
    }

    for (JsonSchemaFileProvider p : myState.getProviders()) {
      if (isSchemaProvider(p) && p.isAvailable(file)) {
        return true;
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

  private @Nullable JsonSchemaVersion getSchemaVersionFromSchemaUrl(@NotNull VirtualFile file) {
    String schemaPropertyValue;
    JsonSchemaObject schemaRootOrNull = JsonSchemaObjectStorage.getInstance(myProject).getComputedSchemaRootOrNull(file);
    if (schemaRootOrNull != null) {
      schemaPropertyValue = schemaRootOrNull.getSchema();
      return schemaPropertyValue == null ? null : JsonSchemaVersion.byId(schemaPropertyValue);
    }

    Ref<String> res = Ref.create(null);
    //noinspection CodeBlock2Expr
    ReadAction.runBlocking(() -> {
      res.set(JsonCachedValues.getSchemaUrlFromSchemaProperty(file, myProject));
    });
    schemaPropertyValue = res.get();
    return schemaPropertyValue == null ? null : JsonSchemaVersion.byId(schemaPropertyValue);
  }

  private boolean hasSchemaSchema(VirtualFile file) {
    return getSchemaVersionFromSchemaUrl(file) != null;
  }

  private static boolean isProviderAvailable(final @NotNull VirtualFile file, @NotNull JsonSchemaFileProvider provider) {
    return provider.isAvailable(file);
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
    private final @NotNull SynchronizedClearableLazy<ProviderIndex> myIndex;

    private MyState(@NotNull Supplier<List<JsonSchemaFileProvider>> factory) {
      myIndex = new SynchronizedClearableLazy<>(() -> new ProviderIndex(factory.get()));
    }

    public void reset() {
      myIndex.drop();
    }

    public void processProviders(@NotNull Consumer<JsonSchemaFileProvider> consumer) {
      myIndex.getValue().providers.forEach(consumer);
    }

    public @NotNull Set<VirtualFile> getFiles() {
      return myIndex.getValue().schemaFiles;
    }

    public @NotNull List<JsonSchemaFileProvider> getProviders() {
      return myIndex.getValue().providers;
    }

    public @Nullable JsonSchemaFileProvider getProvider(@NotNull VirtualFile file) {
      return myIndex.getValue().getProvider(file);
    }

    public @Nullable JsonSchemaFileProvider getProvider(@NotNull VirtualFile schemaFile, @NotNull VirtualFile file) {
      return myIndex.getValue().getProvider(schemaFile, file);
    }

    private boolean isMappedIfComputed(@NotNull VirtualFile file) {
      ProviderIndex index = myIndex.getValueIfInitialized();
      return index != null && index.getProvider(file) != null;
    }
  }

  private static final class ProviderIndex {
    private final @NotNull List<JsonSchemaFileProvider> providers;
    private final @NotNull Map<VirtualFile, JsonSchemaFileProvider> bySchemaFile;
    private final @NotNull Map<VirtualFile, List<JsonSchemaFileProvider>> providersBySchemaFile;
    private final @NotNull Map<JsonSchemaFileProvider, VirtualFile> schemaFileOf;
    private final @NotNull Set<VirtualFile> schemaFiles;

    private ProviderIndex(@NotNull List<JsonSchemaFileProvider> list) {
      List<JsonSchemaFileProvider> providers = new ArrayList<>(list.size());
      Map<VirtualFile, JsonSchemaFileProvider> bySchemaFile = new LinkedHashMap<>();
      Map<VirtualFile, List<JsonSchemaFileProvider>> providersBySchemaFile = new LinkedHashMap<>();
      Map<JsonSchemaFileProvider, VirtualFile> schemaFileOf = new HashMap<>();
      for (JsonSchemaFileProvider provider : list) {
        VirtualFile schemaFile;
        try {
          schemaFile = provider.getSchemaFile();
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception e) {
          LOG.error(e);
          continue;
        }
        providers.add(provider);
        if (schemaFile == null) {
          continue;
        }
        schemaFileOf.put(provider, schemaFile);
        providersBySchemaFile.computeIfAbsent(schemaFile, _ -> new ArrayList<>()).add(provider);
        JsonSchemaFileProvider existing = bySchemaFile.get(schemaFile);
        if (existing == null
            || (existing.getSchemaType() != SchemaType.userSchema && provider.getSchemaType() == SchemaType.userSchema)) {
          bySchemaFile.put(schemaFile, provider);
        }
      }
      this.providers = Collections.unmodifiableList(providers);
      this.bySchemaFile = bySchemaFile;
      this.providersBySchemaFile = providersBySchemaFile;
      this.schemaFileOf = schemaFileOf;
      schemaFiles = Collections.unmodifiableSet(bySchemaFile.keySet());
    }

    private @Nullable JsonSchemaFileProvider getProvider(@NotNull VirtualFile file) {
      if (file.getUserData(SchemaOrigin.REQUEST_URL_KEY) == null) {
        return bySchemaFile.get(file);
      }
      return match(file, null);
    }

    private @Nullable JsonSchemaFileProvider getProvider(@NotNull VirtualFile schemaFile, @NotNull VirtualFile file) {
      if (schemaFile.getUserData(SchemaOrigin.REQUEST_URL_KEY) == null) {
        return selectAvailable(schemaFile, file);
      }
      return match(schemaFile, file);
    }

    private @Nullable JsonSchemaFileProvider match(@NotNull VirtualFile schemaFile, @Nullable VirtualFile availabilityFile) {
      JsonSchemaFileProvider result = null;
      for (JsonSchemaFileProvider provider : providers) {
        boolean matches = schemaFile.equals(schemaFileOf.get(provider)) ||
                          SchemaOrigin.isCachedContentOf(schemaFile, provider.getRemoteSource());
        if (!matches) continue;
        if (availabilityFile != null && !isProviderAvailable(availabilityFile, provider)) continue;
        if (provider.getSchemaType() == SchemaType.userSchema) return provider;
        if (result == null) result = provider;
      }
      return result;
    }

    private @Nullable JsonSchemaFileProvider selectAvailable(@NotNull VirtualFile schemaFile, @NotNull VirtualFile file) {
      List<JsonSchemaFileProvider> candidates = providersBySchemaFile.get(schemaFile);
      if (candidates == null) {
        return null;
      }
      JsonSchemaFileProvider result = null;
      for (JsonSchemaFileProvider provider : candidates) {
        if (!isProviderAvailable(file, provider)) continue;
        if (provider.getSchemaType() == SchemaType.userSchema) return provider;
        if (result == null) result = provider;
      }
      return result;
    }
  }

  private static @Nullable VirtualFile getSchemaForProvider(@NotNull Project project, @NotNull JsonSchemaFileProvider provider) {
    VirtualFile schemaFile = provider.getSchemaFile();
    String source = provider.getRemoteSource();
    boolean preferRemote = JsonSchemaCatalogProjectConfiguration.getInstance(project).isPreferRemoteSchemas();
    if (source != null &&
        (schemaFile == null || (!source.endsWith("!") && preferRemote && !JsonFileResolver.isSchemaUrl(source)))) {
      VirtualFile cachedFile = JsonSchemaRemoteContentService.getInstance(project).getCachedFile(source);
      if (cachedFile != null || schemaFile == null) {
        return cachedFile;
      }
    }
    return schemaFile;
  }

  private @Nullable VirtualFile getAllowedSchemaForProvider(@NotNull VirtualFile referent,
                                                             @NotNull JsonSchemaFileProvider provider) {
    boolean restrictReference = shouldRestrictSchemaReferences(referent);
    VirtualFile schemaFile = restrictReference ? provider.getSchemaFile() : getSchemaForProvider(myProject, provider);
    if (schemaFile == null || !restrictReference || isBundledSchemaProvider(provider)) return schemaFile;
    return isForbiddenAfterResolution(schemaFile) ? null : schemaFile;
  }

  @Override
  public @Nullable VirtualFile resolveSchemaFile(@NotNull JsonSchemaObject schemaObject) {
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

  private final class JsonSchemaProviderFactories {

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
      for (JsonSchemaProviderFactory factory : getProviderFactories()) {
        if (DumbService.getInstance(myProject).isUsableInCurrentContext(factory)) {
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
