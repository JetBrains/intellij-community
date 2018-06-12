// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.remote;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.FileDownloadingAdapter;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.openapi.vfs.impl.http.RemoteFileInfo;
import com.intellij.openapi.vfs.impl.http.RemoteFileManager;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.JsonSchemaCatalogProjectConfiguration;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonCachedValues;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public class JsonSchemaCatalogManager {
  private static final String DEFAULT_CATALOG = "http://schemastore.org/api/json/catalog.json";
  @NotNull private final Project myProject;
  @NotNull private final JsonSchemaRemoteContentProvider myRemoteContentProvider;
  @Nullable private VirtualFile myCatalog = null;
  @NotNull private final ConcurrentMap<String, String> myResolvedMappings = ContainerUtil.newConcurrentMap();
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
    myCatalog = !JsonFileResolver.isRemoteEnabled(myProject) ? null : JsonFileResolver.urlToFile(DEFAULT_CATALOG, myProject);
  }

  @Nullable
  public VirtualFile getSchemaFileForFile(@NotNull VirtualFile file) {
    if (!JsonSchemaCatalogProjectConfiguration.getInstance(myProject).isCatalogEnabled()) return null;
    for (JsonSchemaCatalogExclusion exclusion : JsonSchemaCatalogExclusion.EP_NAME.getExtensions()) {
      if (exclusion.isExcluded(file)) {
        return null;
      }
    }

    String name = file.getName();
    if (myResolvedMappings.containsKey(name)) {
      String urlString = myResolvedMappings.get(name);
      if (EMPTY.equals(urlString)) return null;
      return JsonFileResolver.resolveSchemaByReference(file, urlString, myProject);
    }

    if (myCatalog != null) {
      String urlString = resolveSchemaFile(file, myCatalog, myProject);
      if (NO_CACHE.equals(urlString)) return null;
      myResolvedMappings.put(name, urlString == null ? EMPTY : urlString);
      return JsonFileResolver.resolveSchemaByReference(file, urlString, myProject);
    }

    return null;
  }

  public List<String> getAllCatalogSchemas() {
    if (myCatalog != null) {
      List<Pair<Collection<String>, String>> catalog = JsonCachedValues.getSchemaCatalog(myCatalog, myProject);
      if (catalog == null) return ContainerUtil.emptyList();
      List<String> results = ContainerUtil.newArrayListWithCapacity(catalog.size());
      for (Pair<Collection<String>, String> item: catalog) {
        results.add(item.second);
      }
      return results;
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
          public void fileDownloaded(VirtualFile localFile) {
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

  public void triggerUpdateCatalog() {
    JsonFileResolver.startFetchingHttpFileIfNeeded(myCatalog);
  }

  @Nullable
  private static String resolveSchemaFile(@NotNull VirtualFile file, @NotNull VirtualFile catalogFile, @NotNull Project project) {
    JsonFileResolver.startFetchingHttpFileIfNeeded(catalogFile);

    List<Pair<Collection<String>, String>> schemaCatalog = JsonCachedValues.getSchemaCatalog(catalogFile, project);
    if (schemaCatalog == null) return catalogFile instanceof HttpVirtualFile ? NO_CACHE : null;
    String fileName = file.getName();
    for (Pair<Collection<String>, String> maskAndPath: schemaCatalog) {
      if (matches(fileName, maskAndPath.first)) {
        return maskAndPath.second;
      }
    }

    return null;
  }

  private static boolean matches(@NotNull String fileName, @NotNull Collection<String> masks) {
    for (String mask: masks) {
      if (matches(fileName, mask)) return true;
    }
    return false;
  }

  private static boolean matches(@NotNull String fileName, @NotNull String mask) {
    if (mask.equals(fileName)) return true;
    int star = mask.indexOf('*');

    // no star - no match
    if (star == -1) return false;

    // *.foo.json
    if (star == 0 && fileName.startsWith(mask.substring(1))) {
      return true;
    }

    // foobar*
    if (star == mask.length() - 1 && fileName.endsWith(mask.substring(0, mask.length() - 1))) {
      return true;
    }

    String beforeStar = mask.substring(0, star);
    String afterStar = mask.substring(star + 1);

    if (fileName.startsWith(beforeStar) && fileName.endsWith(afterStar)) {
      return true;
    }
    return false;
  }
}
