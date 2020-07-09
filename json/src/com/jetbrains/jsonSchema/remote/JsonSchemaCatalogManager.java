// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.remote;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.FileDownloadingAdapter;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.openapi.vfs.impl.http.RemoteFileInfo;
import com.intellij.openapi.vfs.impl.http.RemoteFileManager;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.JsonSchemaCatalogEntry;
import com.jetbrains.jsonSchema.JsonSchemaCatalogProjectConfiguration;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonCachedValues;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class JsonSchemaCatalogManager {
  static final String DEFAULT_CATALOG = "http://schemastore.org/api/json/catalog.json";
  static final String DEFAULT_CATALOG_HTTPS = "https://schemastore.azurewebsites.net/api/json/catalog.json";
  private static final Set<String> SCHEMA_URLS_WITH_TOO_MANY_VARIANTS = ImmutableSet.of(
    "https://raw.githubusercontent.com/microsoft/azure-pipelines-vscode/master/service-schema.json"
  );

  private final @NotNull Project myProject;
  private final @NotNull JsonSchemaRemoteContentProvider myRemoteContentProvider;
  private @Nullable VirtualFile myCatalog = null;
  private final @NotNull ConcurrentMap<String, String> myResolvedMappings = new ConcurrentHashMap<>();
  private static final String NO_CACHE = "$_$_WS_NO_CACHE_$_$";
  private static final String EMPTY = "$_$_WS_EMPTY_$_$";
  private VirtualFile myTestSchemaStoreFile;

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
    Application application = ApplicationManager.getApplication();
    if (application != null && application.isUnitTestMode()) {
      myCatalog = myTestSchemaStoreFile;
      return;
    }
    // ignore schema catalog when remote activity is disabled (when we're in tests or it is off in settings)
    myCatalog = !JsonFileResolver.isRemoteEnabled(myProject) ? null : JsonFileResolver.urlToFile(DEFAULT_CATALOG);
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

  public @Nullable VirtualFile getSchemaFileForFile(@NotNull VirtualFile file) {
    if (!JsonSchemaCatalogProjectConfiguration.getInstance(myProject).isCatalogEnabled()) {
      return null;
    }

    if (JsonSchemaCatalogExclusion.EP_NAME.findFirstSafe(exclusion -> exclusion.isExcluded(file)) != null) {
      return null;
    }

    String name = file.getName();
    String schemaUrl = null;
    if (myResolvedMappings.containsKey(name)) {
      schemaUrl = myResolvedMappings.get(name);
      if (EMPTY.equals(schemaUrl)) return null;
    }
    else if (myCatalog != null) {
      schemaUrl = resolveSchemaFile(file, myCatalog, myProject);
      if (NO_CACHE.equals(schemaUrl)) return null;
      myResolvedMappings.put(name, StringUtil.notNullize(schemaUrl, EMPTY));
    }
    if (SCHEMA_URLS_WITH_TOO_MANY_VARIANTS.contains(schemaUrl)) {
      return null;
    }
    return JsonFileResolver.resolveSchemaByReference(file, schemaUrl);
  }

  public List<JsonSchemaCatalogEntry> getAllCatalogEntries() {
    if (myCatalog != null) {
      final List<JsonSchemaCatalogEntry> catalog = JsonCachedValues.getSchemaCatalog(myCatalog, myProject);
      return catalog == null ? ContainerUtil.emptyList() : catalog;
    }
    return ContainerUtil.emptyList();
  }

  private final Map<Runnable, FileDownloadingAdapter> myDownloadingAdapters = CollectionFactory.createConcurrentWeakMap();
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

    List<FileMatcher> fileMatchers = ContainerUtil.map(schemaCatalog, entry -> new FileMatcher(entry));

    String fileRelativePathStr = getRelativePath(file, project);
    String url = findMatchedUrl(fileMatchers, fileRelativePathStr);
    if (url == null) {
      String fileName = file.getName();
      if (!fileName.equals(fileRelativePathStr)) {
        url = findMatchedUrl(fileMatchers, fileName);
      }
    }
    return url;
  }

  private static @Nullable String findMatchedUrl(@NotNull List<FileMatcher> matchers, @Nullable String filePath) {
    if (filePath == null) return null;
    Path path = Paths.get(filePath);
    for (FileMatcher matcher : matchers) {
      if (matcher.matches(path)) {
        return matcher.myEntry.getUrl();
      }
    }
    return null;
  }

  private static @Nullable String getRelativePath(@NotNull VirtualFile file, @NotNull Project project) {
    String basePath = project.getBasePath();
    if (basePath != null) {
      basePath = StringUtil.trimEnd(basePath, VfsUtilCore.VFS_SEPARATOR_CHAR) + VfsUtilCore.VFS_SEPARATOR_CHAR;
      String filePath = file.getPath();
      if (filePath.startsWith(basePath)) {
        return filePath.substring(basePath.length());
      }
    }
    VirtualFile contentRoot = ReadAction.compute(() -> {
      if (project.isDisposed() || !file.isValid()) return null;
      return ProjectFileIndex.getInstance(project).getContentRootForFile(file, false);
    });
    return contentRoot != null ? VfsUtilCore.findRelativePath(contentRoot, file, VfsUtilCore.VFS_SEPARATOR_CHAR) : null;
  }

  private static final class FileMatcher {
    private final JsonSchemaCatalogEntry myEntry;
    private PathMatcher myMatcher;

    private FileMatcher(@NotNull JsonSchemaCatalogEntry entry) {
      myEntry = entry;
    }

    private boolean matches(@NotNull Path filePath) {
      if (myMatcher == null) {
        myMatcher = buildPathMatcher(myEntry.getFileMasks());
      }
      return myMatcher.matches(filePath);
    }

    private static @NotNull PathMatcher buildPathMatcher(@NotNull Collection<String> fileMatches) {
      if (fileMatches.size() == 1) {
        return FileSystems.getDefault().getPathMatcher("glob:" + ContainerUtil.getFirstItem(fileMatches));
      }
      if (fileMatches.size() > 0) {
        return FileSystems.getDefault().getPathMatcher("glob:{" + StringUtil.join(fileMatches, ",") + "}");
      }
      return path -> false;
    }
  }
}
