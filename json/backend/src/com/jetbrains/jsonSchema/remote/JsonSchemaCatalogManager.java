// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.PatternSyntaxException;

public final class JsonSchemaCatalogManager {
  private static final Logger LOG = Logger.getInstance(JsonSchemaCatalogManager.class);

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
  private static final String NO_CACHE = "$_$_WS_NO_CACHE_$_$";
  private static final String EMPTY = "$_$_WS_EMPTY_$_$";
  private VirtualFile myTestSchemaStoreFile;

  private final Map<Runnable, FileDownloadingAdapter> myDownloadingAdapters = CollectionFactory.createConcurrentWeakMap();

  public JsonSchemaCatalogManager(@NotNull Project project, @NotNull Disposable parentDisposable) {
    myProject = project;
    JsonSchemaRemoteContentProvider remoteContentProvider = new JsonSchemaRemoteContentProvider();
    RemoteFileManager.getInstance().addRemoteContentProvider(remoteContentProvider, parentDisposable);
  }

  public void startUpdates(@NotNull Disposable parentDisposable) {
    JsonSchemaCatalogProjectConfiguration.getInstance(myProject).addChangeHandler(() -> {
      update();
      JsonSchemaService.Impl.get(myProject).reset();
    }, parentDisposable);
    update();
  }

  private void update() {
    myResolvedMappings.clear();
    Application application = ApplicationManager.getApplication();
    if (application != null && application.isUnitTestMode()) {
      myCatalog = myTestSchemaStoreFile;
      return;
    }
    // ignore schema catalog when remote activity is disabled (when we're in tests, or it is off in settings)
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

  @TestOnly
  public @Nullable VirtualFile getSchemaFileForFile(@NotNull VirtualFile file) {
    String schemaUrl = getResolvedSchemaUrl(file);
    if (schemaUrl == null || isIgnoredAsHavingTooManyVariants(schemaUrl)) {
      return null;
    }
    return JsonFileResolver.resolveSchemaByReference(file, schemaUrl);
  }

  @Nullable JsonSchemaCatalogEntry getSchemaCatalogEntryForFile(@NotNull VirtualFile file) {
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
      if (NO_CACHE.equals(schemaUrl)) return null;
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

  public void unregisterCatalogUpdateCallback(@NotNull Runnable callback) {
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

    JsonSchemaCatalogEntry entry = resolveSchemaEntry(file, schemaCatalog, project, null);
    return entry == null ? null : entry.getUrl();
  }

  private static @Nullable JsonSchemaCatalogEntry resolveSchemaEntry(@NotNull VirtualFile file,
                                                                     @NotNull VirtualFile catalogFile,
                                                                     @NotNull Project project,
                                                                     @NotNull String schemaUrl) {
    JsonFileResolver.startFetchingHttpFileIfNeeded(catalogFile, project);

    List<JsonSchemaCatalogEntry> schemaCatalog = JsonCachedValues.getSchemaCatalog(catalogFile, project);
    if (schemaCatalog == null) return null;

    return resolveSchemaEntry(file, schemaCatalog, project, schemaUrl);
  }

  private static @Nullable JsonSchemaCatalogEntry resolveSchemaEntry(@NotNull VirtualFile file,
                                                                     @NotNull List<JsonSchemaCatalogEntry> schemaCatalog,
                                                                     @NotNull Project project,
                                                                     @Nullable String schemaUrl) {
    List<FileMatcher> fileMatchers = ContainerUtil.mapNotNull(schemaCatalog,
                                                              entry1 -> schemaUrl == null || schemaUrl.equals(entry1.getUrl())
                                                                        ? new FileMatcher(entry1)
                                                                        : null);

    String fileRelativePathStr = getRelativePath(file, project);
    JsonSchemaCatalogEntry entry = findMatchedEntry(fileMatchers, fileRelativePathStr);
    if (entry == null) {
      String fileName = file.getName();
      if (!fileName.equals(fileRelativePathStr)) {
        entry = findMatchedEntry(fileMatchers, fileName);
      }
    }
    return entry;
  }

  private static @Nullable JsonSchemaCatalogEntry findMatchedEntry(@NotNull List<FileMatcher> matchers, @Nullable String filePath) {
    if (filePath == null) return null;
    Path path;
    try {
      path = Paths.get(filePath);
    }
    catch (InvalidPathException e) {
      LOG.debug("Unable to process invalid path '" + filePath + "'", e);
      return null;
    }

    for (FileMatcher matcher : matchers) {
      try {
        if (matcher.matches(path)) {
          return matcher.myEntry;
        }
      }
      catch (PatternSyntaxException pse) {
        LOG.warn("Unable to process matches for path '" + path + "' with matcher URL '" + matcher.myEntry.getUrl() + "'", pse);
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
    VirtualFile contentRoot = ReadAction.computeBlocking(() -> {
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

    private static @NotNull PathMatcher buildPathMatcher(@NotNull Collection<String> fileMasks) {
      List<String> refinedFileMasks = ContainerUtil.map(fileMasks, fileMask -> StringUtil.trimStart(fileMask, "**/"));
      if (refinedFileMasks.size() == 1) {
        return FileSystems.getDefault().getPathMatcher("glob:" + ContainerUtil.getFirstItem(refinedFileMasks));
      }
      if (!refinedFileMasks.isEmpty()) {
        return FileSystems.getDefault().getPathMatcher("glob:{" + StringUtil.join(refinedFileMasks, ",") + "}");
      }
      return path -> false;
    }
  }
}
