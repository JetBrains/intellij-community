// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class JsonSchemaInfo {
  private final JsonSchemaFileProvider myProvider;
  private final String myUrl;

  public JsonSchemaInfo(@NotNull JsonSchemaFileProvider provider) {
    myProvider = provider;
    myUrl = null;
  }

  public JsonSchemaInfo(@NotNull String url) {
    myUrl = url;
    myProvider = null;
  }

  public JsonSchemaFileProvider getProvider() {
    return myProvider;
  }

  public String getUrl(Project project) {
    if (myProvider != null) {
      String remoteSource = myProvider.getRemoteSource();
      if (remoteSource != null) {
        return remoteSource;
      }

      VirtualFile schemaFile = myProvider.getSchemaFile();
      if (schemaFile == null) return "";

      if (schemaFile instanceof HttpVirtualFile) {
        return schemaFile.getUrl();
      }

      return getRelativePath(project, schemaFile.getPath());
    }
    else {
      return myUrl;
    }
  }

  public String getDescription() {
    if (myProvider != null) {
      String providerName = myProvider.getPresentableName();
      return sanitizeName(providerName);
    }

    assert myUrl != null;
    int index = StringUtil.lastIndexOfAny(myUrl, "/\\");
    if (index == -1) return sanitizeName(myUrl);
    String possibleName = sanitizeName(myUrl.substring(index + 1));
    if (!isVeryDumbName(possibleName)) {
      return possibleName;
    }

    int index2 = myUrl.lastIndexOf('/', index - 1);
    if (index2 != -1) {
      possibleName = sanitizeName(myUrl.substring(index2 + 1, index));
    }

    // the only weird case
    if ("http://json.schemastore.org/config".equals(myUrl)) {
      return "asp.net config";
    }

    if (!isVeryDumbName(possibleName)) {
      return possibleName;
    }

    if (myUrl.startsWith("https://raw.githubusercontent.com/")) {
      String substring = myUrl.substring("https://raw.githubusercontent.com/".length() + 1);
      int slash = substring.indexOf('/');
      if (slash != -1) {
        int slash2 = substring.indexOf('/', slash + 1);
        if (slash2 != -1) {
          return sanitizeName(substring.substring(slash + 1, slash2));
        }
      }
    }

    return possibleName;
  }

  public static boolean isVeryDumbName(String possibleName) {
    return "schema".equals(possibleName) || "config".equals(possibleName);
  }

  @NotNull
  private static String sanitizeName(String providerName) {
    return StringUtil.trimEnd(StringUtil.trimEnd(StringUtil.trimEnd(providerName, ".json"), "-schema"), ".schema");
  }

  public JsonSchemaVersion getSchemaVersion() {
    return myProvider != null ? myProvider.getSchemaVersion() : JsonSchemaVersion.SCHEMA_4;
  }

  public static String getRelativePath(@NotNull Project project, @NotNull String text) {
    text = text.trim();
    if (project.isDefault() || project.getBasePath() == null) return text;
    if (StringUtil.isEmptyOrSpaces(text)) return text;
    final File ioFile = new File(text);
    if (!ioFile.isAbsolute()) return text;
    VirtualFile file = VfsUtil.findFileByIoFile(ioFile, false);
    if (file == null) return text;
    final String relativePath = VfsUtilCore.getRelativePath(file, project.getBaseDir());
    if (relativePath != null) return relativePath;
    if (isMeaningfulAncestor(VfsUtilCore.getCommonAncestor(file, project.getBaseDir()))) {
      String path = VfsUtilCore.findRelativePath(project.getBaseDir(), file, File.separatorChar);
      if (path != null) return path;
    }
    return text;
  }

  private static boolean isMeaningfulAncestor(@Nullable VirtualFile ancestor) {
    if (ancestor == null) return false;
    VirtualFile homeDir = VfsUtil.getUserHomeDir();
    return homeDir != null && VfsUtilCore.isAncestor(homeDir, ancestor, true);
  }
}
