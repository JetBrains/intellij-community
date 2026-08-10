// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.JsonSchemaCatalogEntry;
import com.jetbrains.jsonSchema.JsonSchemaCatalogProjectConfiguration;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonCachedValues;
import com.jetbrains.jsonSchema.remote.http.JsonSchemaRemoteContentService;
import com.jetbrains.jsonSchema.remote.http.RemoteSchemaDownloadListener;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

public final class JsonSchemaCatalogManager {
  static final String DEFAULT_CATALOG = "http://schemastore.org/api/json/catalog.json";
  static final String DEFAULT_CATALOG_HTTPS = "https://schemastore.org/api/json/catalog.json";
  private static final Set<String> SCHEMA_URL_PREFIXES_WITH_TOO_MANY_VARIANTS = Set.of(
    // To match changing schema URLs for azure-pipelines:
    // - https://raw.githubusercontent.com/microsoft/azure-pipelines-vscode/master/service-schema.json
    // - https://raw.githubusercontent.com/microsoft/azure-pipelines-vscode/v1.174.2/service-schema.json
    "https://raw.githubusercontent.com/microsoft/azure-pipelines-vscode/"
  );

  private final @NotNull Project myProject;
  private @Nullable VirtualFile myCatalog = null;
  private final @NotNull ConcurrentMap<VirtualFile, String> myResolvedMappings = ContainerUtil.createConcurrentSoftMap();
  private static final String EMPTY = "$_$_WS_EMPTY_$_$";
  private VirtualFile myTestSchemaStoreFile;

  private final Map<Runnable, Disposable> myCatalogUpdateListeners = CollectionFactory.createConcurrentWeakMap();

  public JsonSchemaCatalogManager(@NotNull Project project) {
    myProject = project;
  }

  public void startUpdates(@NotNull Disposable parentDisposable) {
    JsonSchemaCatalogProjectConfiguration.getInstance(myProject).addChangeHandler(() -> {
      update();
      JsonSchemaService.Impl.get(myProject).reset();
    }, parentDisposable);
    update();
  }

  @ApiStatus.Internal
  public void updateIfDownloaded(@NotNull Collection<String> urls) {
    for (String url : urls) {
      if (DEFAULT_CATALOG_HTTPS.equals(url)) {
        update();
        return;
      }
    }
  }

  private void update() {
    myResolvedMappings.clear();
    Application application = ApplicationManager.getApplication();
    if (application != null && application.isUnitTestMode()) {
      myCatalog = myTestSchemaStoreFile;
      return;
    }
    // ignore schema catalog when remote activity is disabled (when we're in tests, or it is off in settings)
    // non-blocking: returns the cached catalog file if present, otherwise schedules a download and returns null;
    // the batched download consumer in JsonSchemaVfsListener calls updateIfDownloaded once per batch.
    myCatalog = !JsonFileResolver.isRemoteEnabled(myProject) ? null :
                JsonSchemaRemoteContentService.getInstance(myProject).getCachedFile(DEFAULT_CATALOG_HTTPS);
  }

  @TestOnly
  public void registerTestSchemaStoreFile(@NotNull VirtualFile testSchemaStoreFile, @NotNull Disposable testDisposable) {
    myTestSchemaStoreFile = testSchemaStoreFile;
    Disposer.register(testDisposable, () -> {
      myTestSchemaStoreFile = null;
      update();
    });
    update();
  }

  @TestOnly
  public @Nullable VirtualFile getSchemaFileForFile(@NotNull VirtualFile file) {
    String schemaUrl = getResolvedSchemaUrl(file);
    if (schemaUrl == null || isIgnoredAsHavingTooManyVariants(schemaUrl)) {
      return null;
    }
    return JsonFileResolver.resolveSchemaByReference(file, schemaUrl, myProject);
  }

  @ApiStatus.Internal
  public @Nullable JsonSchemaCatalogEntry getSchemaCatalogEntryForFile(@NotNull VirtualFile file) {
    String schemaUrl = getResolvedSchemaUrl(file);
    if (schemaUrl == null || isIgnoredAsHavingTooManyVariants(schemaUrl) || myCatalog == null) {
      return null;
    }
    return resolveSchemaEntry(file, myCatalog, myProject, schemaUrl);
  }

  private @Nullable String getResolvedSchemaUrl(@NotNull VirtualFile file) {
    if (!JsonSchemaCatalogProjectConfiguration.getInstance(myProject).isCatalogEnabled()) {
      return null;
    }

    if (JsonSchemaCatalogExclusion.EP_NAME.findFirstSafe(exclusion -> exclusion.isExcluded(file)) != null) {
      return null;
    }

    String schemaUrl = myResolvedMappings.get(file);
    if (EMPTY.equals(schemaUrl)) {
      return null;
    }
    if (schemaUrl == null && myCatalog != null) {
      schemaUrl = resolveSchemaFile(file, myCatalog, myProject);
      myResolvedMappings.put(file, StringUtil.notNullize(schemaUrl, EMPTY));
    }
    return schemaUrl;
  }

  private static boolean isIgnoredAsHavingTooManyVariants(@NotNull String schemaUrl) {
    return ContainerUtil.exists(SCHEMA_URL_PREFIXES_WITH_TOO_MANY_VARIANTS, prefix -> schemaUrl.startsWith(prefix));
  }

  public List<JsonSchemaCatalogEntry> getAllCatalogEntries() {
    if (myCatalog != null) {
      final List<JsonSchemaCatalogEntry> catalog = JsonCachedValues.getSchemaCatalog(myCatalog, myProject);
      return catalog == null ? ContainerUtil.emptyList() : catalog;
    }
    return ContainerUtil.emptyList();
  }

  public void registerCatalogUpdateCallback(@NotNull Runnable callback) {
    Disposable disposable = Disposer.newDisposable();
    myCatalogUpdateListeners.put(callback, disposable);
    myProject.getMessageBus().connect(disposable).subscribe(RemoteSchemaDownloadListener.TOPIC, url -> {
      if (DEFAULT_CATALOG_HTTPS.equals(url)) {
        callback.run();
      }
    });
  }

  public void unregisterCatalogUpdateCallback(@NotNull Runnable callback) {
    Disposable disposable = myCatalogUpdateListeners.remove(callback);
    if (disposable != null) {
      Disposer.dispose(disposable);
    }
  }

  public void triggerUpdateCatalog(Project project) {
    // an explicit user request, so try again even after a failed download
    JsonSchemaRemoteContentService.getInstance(project).retryDownload(DEFAULT_CATALOG_HTTPS);
  }

  private static @Nullable String resolveSchemaFile(@NotNull VirtualFile file, @NotNull VirtualFile catalogFile, @NotNull Project project) {
    List<JsonSchemaCatalogEntry> schemaCatalog = JsonCachedValues.getSchemaCatalog(catalogFile, project);
    if (schemaCatalog == null) return null;

    JsonSchemaCatalogEntry entry = resolveSchemaEntry(file, schemaCatalog, project, null);
    return entry == null ? null : entry.getUrl();
  }

  private static @Nullable JsonSchemaCatalogEntry resolveSchemaEntry(@NotNull VirtualFile file,
                                                                     @NotNull VirtualFile catalogFile,
                                                                     @NotNull Project project,
                                                                     @NotNull String schemaUrl) {
    List<JsonSchemaCatalogEntry> schemaCatalog = JsonCachedValues.getSchemaCatalog(catalogFile, project);
    if (schemaCatalog == null) return null;

    return resolveSchemaEntry(file, schemaCatalog, project, schemaUrl);
  }

  private static @Nullable JsonSchemaCatalogEntry resolveSchemaEntry(@NotNull VirtualFile file,
                                                                     @NotNull List<JsonSchemaCatalogEntry> schemaCatalog,
                                                                     @NotNull Project project,
                                                                     @Nullable String schemaUrl) {
    List<JsonSchemaCatalogEntryFileMatcher> fileMatchers = ContainerUtil.mapNotNull(schemaCatalog,
                                                                                    entry1 -> schemaUrl == null || schemaUrl.equals(entry1.getUrl())
                                                                                              ? new JsonSchemaCatalogEntryFileMatcher(entry1)
                                                                                              : null);

    return findMatchedEntry(fileMatchers, file, project);
  }

  private static @Nullable JsonSchemaCatalogEntry findMatchedEntry(@NotNull List<JsonSchemaCatalogEntryFileMatcher> matchers,
                                                                   @NotNull VirtualFile file,
                                                                   @NotNull Project project) {
    for (JsonSchemaCatalogEntryFileMatcher matcher : matchers) {
      if (matcher.matches(file, project)) {
        return matcher.getEntry();
      }
    }
    return null;
  }
}
