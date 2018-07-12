// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.remote;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.openapi.vfs.impl.http.RemoteFileInfo;
import com.intellij.openapi.vfs.impl.http.RemoteFileState;
import com.intellij.util.UriUtil;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.jetbrains.jsonSchema.JsonSchemaCatalogProjectConfiguration;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class JsonFileResolver {
  public static boolean isRemoteEnabled(Project project) {
    return !ApplicationManager.getApplication().isUnitTestMode() &&
           JsonSchemaCatalogProjectConfiguration.getInstance(project).isRemoteActivityEnabled();
  }

  @Nullable
  public static VirtualFile urlToFile(@NotNull String urlString) {
    return VirtualFileManager.getInstance().findFileByUrl(replaceUnsafeSchemaStoreUrls(urlString));
  }

  @Nullable
  @Contract("null -> null; !null -> !null")
  public static String replaceUnsafeSchemaStoreUrls(@Nullable String urlString) {
    if (urlString == null) return null;
    if (urlString.equals(JsonSchemaCatalogManager.DEFAULT_CATALOG)) {
      return JsonSchemaCatalogManager.DEFAULT_CATALOG_HTTPS;
    }
    if (StringUtil.startsWithIgnoreCase(urlString, JsonSchemaRemoteContentProvider.STORE_URL_PREFIX_HTTP)) {
      return StringUtil.replace(urlString, "http://json.schemastore.org/", "https://schemastore.azurewebsites.net/schemas/json/") + ".json";
    }
    return urlString;
  }

  @Nullable
  public static VirtualFile resolveSchemaByReference(@Nullable VirtualFile currentFile,
                                                     @Nullable String schemaUrl) {
    if (schemaUrl == null) return null;

    boolean isHttpPath = isHttpPath(schemaUrl);

    if (StringUtil.startsWithChar(schemaUrl, '.') || !isHttpPath) {
      // relative path
      VirtualFile parent = currentFile == null ? null : currentFile.getParent();
      schemaUrl = parent == null ? null : VfsUtilCore.pathToUrl(parent.getPath() + File.separator + schemaUrl);
    }

    if (schemaUrl != null) {
      VirtualFile virtualFile = urlToFile(schemaUrl);
      // validate the URL before returning the file
      if (virtualFile instanceof HttpVirtualFile) {
        String url = virtualFile.getUrl();
        Url parse = Urls.parse(url, false);
        if (parse == null || StringUtil.isEmpty(parse.getAuthority()) || StringUtil.isEmpty(parse.getPath())) return null;
      }
      if (virtualFile != null) return virtualFile;
    }

    return null;
  }

  public static void startFetchingHttpFileIfNeeded(@Nullable VirtualFile path, Project project) {
    if (!(path instanceof HttpVirtualFile)) return;

    // don't resolve http paths in tests
    if (!isRemoteEnabled(project)) return;

    RemoteFileInfo info = ((HttpVirtualFile)path).getFileInfo();
    if (info == null || info.getState() == RemoteFileState.DOWNLOADING_NOT_STARTED) {
      path.refresh(true, false);
    }
  }

  public static boolean isHttpPath(@NotNull String schemaFieldText) {
    Couple<String> couple = UriUtil.splitScheme(schemaFieldText);
    return couple.first.startsWith("http");
  }
}
