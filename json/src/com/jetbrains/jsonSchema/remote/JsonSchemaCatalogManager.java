// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.remote;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.FileDownloadingAdapter;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.openapi.vfs.impl.http.RemoteFileInfo;
import com.intellij.openapi.vfs.impl.http.RemoteFileManager;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.JsonSchemaCatalogEntry;
import com.jetbrains.jsonSchema.JsonSchemaCatalogProjectConfiguration;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonCachedValues;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class JsonSchemaCatalogManager {
  static final String DEFAULT_CATALOG = "http://schemastore.org/api/json/catalog.json";
  static final String DEFAULT_CATALOG_HTTPS = "https://schemastore.azurewebsites.net/api/json/catalog.json";
  private final @NotNull Project myProject;
  private final @NotNull JsonSchemaRemoteContentProvider myRemoteContentProvider;
  private @Nullable VirtualFile myCatalog = null;
  private final @NotNull ConcurrentMap<String, String> myResolvedMappings = new ConcurrentHashMap<>();
  private static final String NO_CACHE = "$_$_WS_NO_CACHE_$_$";
  private static final String EMPTY = "$_$_WS_EMPTY_$_$";

  public JsonSchemaCatalogManager(@NotNull Project project) {
    myProject = project;
    myRemoteContentProvider = new JsonSchemaRemoteContentProvider();
  }

  public void startUpdates() {
    JsonSchemaCatalogProjectConfiguration.getInstance(myProject).addChangeHandler(() -> {
      update();
      JsonSchemaService.Impl.get(myProject).reset();
    });
    RemoteFileManager instance = RemoteFileManager.getInstance();
    instance.addRemoteContentProvider(myRemoteContentProvider);
    update();
  }

  private void update() {
    // ignore schema catalog when remote activity is disabled (when we're in tests or it is off in settings)
    myCatalog = !JsonFileResolver.isRemoteEnabled(myProject) ? null : JsonFileResolver.urlToFile(DEFAULT_CATALOG);
  }

  public @Nullable VirtualFile getSchemaFileForFile(@NotNull VirtualFile file) {
    if (!JsonSchemaCatalogProjectConfiguration.getInstance(myProject).isCatalogEnabled()) {
      return null;
    }

    if (JsonSchemaCatalogExclusion.EP_NAME.findFirstSafe(exclusion -> exclusion.isExcluded(file)) != null) {
      return null;
    }

    String name = file.getName();
    if (myResolvedMappings.containsKey(name)) {
      String urlString = myResolvedMappings.get(name);
      if (EMPTY.equals(urlString)) return null;
      return JsonFileResolver.resolveSchemaByReference(file, urlString);
    }

    if (myCatalog != null) {
      String urlString = resolveSchemaFile(file, myCatalog, myProject);
      if (NO_CACHE.equals(urlString)) return null;
      myResolvedMappings.put(name, urlString == null ? EMPTY : urlString);
      return JsonFileResolver.resolveSchemaByReference(file, urlString);
    }

    return null;
  }

  public List<JsonSchemaCatalogEntry> getAllCatalogEntries() {
    if (myCatalog != null) {
      final List<JsonSchemaCatalogEntry> catalog = JsonCachedValues.getSchemaCatalog(myCatalog, myProject);
      return catalog == null ? ContainerUtil.emptyList() : catalog;
    }
    return ContainerUtil.emptyList();
  }

  private final Map<Runnable, FileDownloadingAdapter> myDownloadingAdapters = ContainerUtil.createConcurrentWeakMap();
  public void registerCatalogUpdateCallback(Runnable callback) {
    if (myCatalog instanceof HttpVirtualFile) {
      RemoteFileInfo info = ((HttpVirtualFile)myCatalog).getFileInfo();
      if (info != null) {
        FileDownloadingAdapter adapter = new FileDownloadingAdapter() {
          @Override
          public void fileDownloaded(@NotNull VirtualFile localFile) {
            callback.run();
          }
        };
        myDownloadingAdapters.put(callback, adapter);
        info.addDownloadingListener(adapter);
      }
    }
  }

  public void unregisterCatalogUpdateCallback(Runnable callback) {
    if (!myDownloadingAdapters.containsKey(callback)) return;

    if (myCatalog instanceof HttpVirtualFile) {
      RemoteFileInfo info = ((HttpVirtualFile)myCatalog).getFileInfo();
      if (info != null) {
        info.removeDownloadingListener(myDownloadingAdapters.get(callback));
      }
    }
  }

  public void triggerUpdateCatalog(Project project) {
    JsonFileResolver.startFetchingHttpFileIfNeeded(myCatalog, project);
  }

  private static @Nullable String resolveSchemaFile(@NotNull VirtualFile file, @NotNull VirtualFile catalogFile, @NotNull Project project) {
    JsonFileResolver.startFetchingHttpFileIfNeeded(catalogFile, project);

    List<JsonSchemaCatalogEntry> schemaCatalog = JsonCachedValues.getSchemaCatalog(catalogFile, project);
    if (schemaCatalog == null) return catalogFile instanceof HttpVirtualFile ? NO_CACHE : null;

    Path fileName = Paths.get(file.getName());
    Path relPath = getRelPath(file, project);
    for (JsonSchemaCatalogEntry maskAndPath: schemaCatalog) {
      if (matches(fileName, relPath, maskAndPath.getFileMasks())) {
        return maskAndPath.getUrl();
      }
    }

    return null;
  }

  @Nullable
  private static Path getRelPath(VirtualFile file, Project project) {
    String basePath = project.getBasePath();
    if (basePath == null) {
      return null;
    }

    String filePath = file.getPath();
    if (!filePath.startsWith(basePath)) {
      return null;
    }

    return Paths.get(basePath).relativize(Paths.get(filePath));
  }

  private static boolean matches(@NotNull Path fileName, @Nullable Path relPath, @NotNull Collection<String> masks) {
    for (String mask: masks) {
      if (matches(fileName, relPath, mask)) return true;
    }
    return false;
  }

  private static boolean matches(@NotNull Path fileName, @Nullable Path relPath, @NotNull String mask) {
    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:"+mask);
    if (matcher.matches(fileName)) return true;
    if (relPath != null && matcher.matches(relPath)) return true;
    return false;
  }
}
