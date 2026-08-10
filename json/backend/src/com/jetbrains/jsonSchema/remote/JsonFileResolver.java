// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.util.PathUtil;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.concurrency.SameThreadExecutor;
import com.jetbrains.jsonSchema.JsonSchemaCatalogProjectConfiguration;
import com.jetbrains.jsonSchema.remote.http.JsonSchemaRemoteContentService;
import com.jetbrains.jsonSchema.remote.http.SchemaOrigin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

import java.net.URI;
import java.util.concurrent.TimeUnit;

public final class JsonFileResolver {

  private static final String STORE_URL_PREFIX_HTTP = "http://json.schemastore.org";
  private static final Logger LOG = Logger.getInstance(JsonFileResolver.class);

  /** Allows remote schema activity in unit test mode. Set it with {@link TestModeFlags}. */
  @ApiStatus.Internal
  public static final Key<Boolean> REMOTE_ENABLED_IN_TESTS = Key.create("json.schema.remote.enabled.in.tests");

  public static boolean isRemoteEnabled(Project project) {
    return (!ApplicationManager.getApplication().isUnitTestMode() || TestModeFlags.is(REMOTE_ENABLED_IN_TESTS)) &&
           JsonSchemaCatalogProjectConfiguration.getInstance(project).isRemoteActivityEnabled();
  }

  public static @Nullable VirtualFile urlToFile(@NotNull String urlString) {
    String tempPath = tempVfsPath(urlString);
    if (tempPath != null) {
      return TempFileSystem.getInstance().findFileByPath(tempPath);
    }
    return VirtualFileManager.getInstance().findFileByUrl(PathUtil.toSystemIndependentName(replaceUnsafeSchemaStoreUrls(urlString)));
  }

  @Contract("null -> null; !null -> !null")
  public static @Nullable String replaceUnsafeSchemaStoreUrls(@Nullable String urlString) {
    if (urlString == null) return null;
    if (urlString.equals(JsonSchemaCatalogManager.DEFAULT_CATALOG)) {
      return JsonSchemaCatalogManager.DEFAULT_CATALOG_HTTPS;
    }
    if (StringUtil.startsWithIgnoreCase(urlString, STORE_URL_PREFIX_HTTP)) {
      String newUrl = StringUtil.replace(urlString, "http://json.schemastore.org/", "https://schemastore.azurewebsites.net/schemas/json/");
      return newUrl.endsWith(".json") ? newUrl : newUrl + ".json";
    }
    return urlString;
  }

  @TestOnly
  public static @Nullable VirtualFile resolveSchemaByReference(@Nullable VirtualFile currentFile,
                                                               @Nullable String schemaUrl) {
    return resolveSchemaByReference(currentFile, schemaUrl, null);
  }

  public static @Nullable VirtualFile resolveSchemaByReference(@Nullable VirtualFile currentFile,
                                                               @Nullable String schemaUrl,
                                                               @Nullable Project project) {
    schemaUrl = resolveSchemaUrlByReference(currentFile, schemaUrl);
    if (schemaUrl == null) return null;

    if (!schemaUrl.startsWith("http")) {
      return urlToFile(schemaUrl);
    }
    if (project != null) {
      return JsonSchemaRemoteContentService.getInstance(project).getCachedFile(schemaUrl);
    }
    return getOrComputeVirtualFileForValidUrlOrNull(schemaUrl);
  }

  @ApiStatus.Internal
  public static @Nullable String resolveSchemaUrlByReference(@Nullable VirtualFile currentFile,
                                                             @Nullable String schemaUrl) {
    if (schemaUrl == null || StringUtil.isEmpty(schemaUrl)) return null;

    if (isAbsoluteUrl(schemaUrl)) return schemaUrl;

    if (currentFile == null) return schemaUrl;

    String originUrl = currentFile.getUserData(SchemaOrigin.URL_KEY);
    if (originUrl == null && isHttpPath(currentFile.getUrl())) {
      originUrl = currentFile.getUrl();
    }
    String resolved = originUrl != null ? resolveAgainstRemoteUrl(originUrl, schemaUrl) : resolveAgainstFile(currentFile, schemaUrl);
    return StringUtil.isEmpty(resolved) ? null : resolved;
  }

  private static @Nullable String resolveAgainstRemoteUrl(@NotNull String originUrl, @NotNull String reference) {
    try {
      // URI rejects a literal space, but a hand-written $ref can contain one.
      return URI.create(originUrl).resolve(StringUtil.replace(reference, " ", "%20")).normalize().toString();
    }
    catch (IllegalArgumentException e) {
      LOG.debug("Unable to resolve schema reference '" + reference + "' against origin '" + originUrl + "'", e);
      return null;
    }
  }

  /**
   * Resolves on the VFS path instead of a {@link URI}.
   * A VFS URL is not URI-encoded, so a space in a directory name breaks {@link URI#create},
   * and {@link URI#getPath} drops the Windows drive of {@code file://C:/...}.
   */
  private static @NotNull String resolveAgainstFile(@NotNull VirtualFile file, @NotNull String reference) {
    int fragmentStart = reference.indexOf('#');
    String referencePath = fragmentStart < 0 ? reference : reference.substring(0, fragmentStart);
    String path;
    if (referencePath.isEmpty()) {
      path = file.getPath();
    }
    else if (referencePath.startsWith("/")) {
      path = FileUtil.toCanonicalPath(referencePath);
    }
    else {
      path = FileUtil.toCanonicalPath(PathUtil.getParentPath(file.getPath()) + "/" + referencePath);
    }
    String url = VirtualFileManager.constructUrl(file.getFileSystem().getProtocol(), path);
    return fragmentStart < 0 ? url : url + reference.substring(fragmentStart);
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

  public static boolean isHttpPath(@NotNull String schemaFieldText) {
    return schemaFieldText.startsWith("http://") || schemaFieldText.startsWith("https://");
  }

  public static boolean isAbsoluteUrl(@NotNull String path) {
    return VirtualFileManager.extractProtocol(path) != null;
  }

  private static final String MOCK_URL = "mock:///";
  public static final String TEMP_URL = "temp:///";
  private static final String TEMP_SCHEME_PREFIX = "temp:";

  private static @Nullable String tempVfsPath(@NotNull String urlString) {
    if (!urlString.startsWith(TEMP_SCHEME_PREFIX)) return null;
    String path = urlString.substring(TEMP_SCHEME_PREFIX.length());
    int fragment = path.indexOf('#');
    if (fragment >= 0) {
      path = path.substring(0, fragment);
    }
    return "/" + StringUtil.trimStart(path, "/");
  }

  public static boolean isTempOrMockUrl(@NotNull String path) {
    return path.startsWith(TEMP_URL) || path.startsWith(MOCK_URL);
  }

  public static boolean isSchemaUrl(@Nullable String url) {
    return url != null && url.startsWith("http://json-schema.org/") && (url.endsWith("/schema") || url.endsWith("/schema#"));
  }
}
