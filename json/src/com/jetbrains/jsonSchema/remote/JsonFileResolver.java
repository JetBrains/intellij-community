// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.openapi.vfs.impl.http.RemoteFileInfo;
import com.intellij.openapi.vfs.impl.http.RemoteFileState;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.concurrency.SameThreadExecutor;
import com.jetbrains.jsonSchema.JsonSchemaCatalogProjectConfiguration;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.TimeUnit;

public final class JsonFileResolver {

  private static final Key<Boolean> DOWNLOAD_STARTED = Key.create("DOWNLOAD_STARTED");

  public static boolean isRemoteEnabled(Project project) {
    return !ApplicationManager.getApplication().isUnitTestMode() &&
           JsonSchemaCatalogProjectConfiguration.getInstance(project).isRemoteActivityEnabled();
  }

  public static @Nullable VirtualFile urlToFile(@NotNull String urlString) {
    if (urlString.startsWith(TEMP_URL)) {
      return TempFileSystem.getInstance().findFileByPath(urlString.substring(TEMP_URL.length() - 1));
    }
    return VirtualFileManager.getInstance().findFileByUrl(FileUtil.toSystemIndependentName(replaceUnsafeSchemaStoreUrls(urlString)));
  }

  @Contract("null -> null; !null -> !null")
  public static @Nullable String replaceUnsafeSchemaStoreUrls(@Nullable String urlString) {
    if (urlString == null) return null;
    if (urlString.equals(JsonSchemaCatalogManager.DEFAULT_CATALOG)) {
      return JsonSchemaCatalogManager.DEFAULT_CATALOG_HTTPS;
    }
    if (StringUtil.startsWithIgnoreCase(urlString, JsonSchemaRemoteContentProvider.STORE_URL_PREFIX_HTTP)) {
      String newUrl = StringUtil.replace(urlString, "http://json.schemastore.org/", "https://schemastore.azurewebsites.net/schemas/json/");
      return newUrl.endsWith(".json") ? newUrl : newUrl + ".json";
    }
    return urlString;
  }

  public static @Nullable VirtualFile resolveSchemaByReference(@Nullable VirtualFile currentFile,
                                                               @Nullable String schemaUrl) {
    if (schemaUrl == null || StringUtil.isEmpty(schemaUrl)) return null;

    boolean isHttpPath = isHttpPath(schemaUrl);

    if (!isHttpPath && currentFile instanceof HttpVirtualFile) {
      // relative http paths
      String url = StringUtil.trimEnd(currentFile.getUrl(), "/");
      int lastSlash = url.lastIndexOf('/');
      assert lastSlash != -1;
      schemaUrl = url.substring(0, lastSlash) + "/" + schemaUrl;
    }
    else if (StringUtil.startsWithChar(schemaUrl, '.') || !isHttpPath) {
      // relative path
      VirtualFile parent = currentFile == null ? null : currentFile.getParent();
      schemaUrl = parent == null ? null :
                  parent.getUrl().startsWith(TEMP_URL) ? ("temp:///" + parent.getPath() + "/" + schemaUrl) :
                  VfsUtilCore.pathToUrl(parent.getPath() + File.separator + schemaUrl);
    }

    if (StringUtil.isEmpty(schemaUrl)) {
      return null;
    }
    else if (!schemaUrl.startsWith("http") || !Registry.is("json.schema.object.v2")) {
      return urlToFile(schemaUrl);
    }
    else {
      return getOrComputeVirtualFileForValidUrlOrNull(schemaUrl);
    }
  }

  private static @Nullable VirtualFile getOrComputeVirtualFileForValidUrlOrNull(@NotNull String maybeUrl) {
    return urlValidityCache.get(maybeUrl);
  }

  // Expirable cache used for cases when:
  //  - user is typing the url
  //  - url became invalid by the time
  private static final LoadingCache<String, VirtualFile> urlValidityCache =
    Caffeine.newBuilder()
      .expireAfterAccess(Registry.intValue("remote.schema.cache.validity.duration", 1), TimeUnit.MINUTES)
      .maximumSize(1000)
      .executor(SameThreadExecutor.INSTANCE)
      .build(JsonFileResolver::computeVirtualFileForValidUrlOrNull);

  private static @Nullable VirtualFile computeVirtualFileForValidUrlOrNull(@NotNull String url) {
    Url parse = Urls.parse(url, false);
    if (parse == null || StringUtil.isEmpty(parse.getAuthority()) || StringUtil.isEmpty(parse.getPath())) return null;
    return urlToFile(url);
  }

  public static void startFetchingHttpFileIfNeeded(@Nullable VirtualFile path, Project project) {
    if (!(path instanceof HttpVirtualFile)) return;

    // don't resolve http paths in tests
    if (!isRemoteEnabled(project)) return;

    RemoteFileInfo info = ((HttpVirtualFile)path).getFileInfo();
    if (info == null || info.getState() == RemoteFileState.DOWNLOADING_NOT_STARTED) {
      if (path.getUserData(DOWNLOAD_STARTED) != Boolean.TRUE) {
        path.putUserData(DOWNLOAD_STARTED, Boolean.TRUE);
        path.refresh(true, false);
      }
    }
  }

  public static boolean isHttpPath(@NotNull String schemaFieldText) {
    return schemaFieldText.startsWith("http://") || schemaFieldText.startsWith("https://");
  }

  public static boolean isAbsoluteUrl(@NotNull String path) {
    return isHttpPath(path) ||
           path.startsWith(TEMP_URL) ||
           FileUtil.toSystemIndependentName(path).startsWith(JarFileSystem.PROTOCOL_PREFIX);
  }

  private static final String MOCK_URL = "mock:///";
  public static final String TEMP_URL = "temp:///";

  public static boolean isTempOrMockUrl(@NotNull String path) {
    return path.startsWith(TEMP_URL) || path.startsWith(MOCK_URL);
  }

  public static boolean isSchemaUrl(@Nullable String url) {
    return url != null && url.startsWith("http://json-schema.org/") && (url.endsWith("/schema") || url.endsWith("/schema#"));
  }
}
