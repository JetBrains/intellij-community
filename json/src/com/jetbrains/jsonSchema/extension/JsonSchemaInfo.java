// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.impl.JsonSchemaType;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Set;

public class JsonSchemaInfo {
  @Nullable private final JsonSchemaFileProvider myProvider;
  @Nullable private final String myUrl;
  @NotNull  private final static Set<String> myDumbNames = ContainerUtil.set(
    "schema",
    "lib",
    "cli",
    "packages",
    "master",
    "format",
    "angular", // the only angular-related schema is the 'angular-cli', so we skip the repo name
    "config");

  public JsonSchemaInfo(@NotNull JsonSchemaFileProvider provider) {
    myProvider = provider;
    myUrl = null;
  }

  public JsonSchemaInfo(@NotNull String url) {
    myUrl = url;
    myProvider = null;
  }

  @Nullable
  public JsonSchemaFileProvider getProvider() {
    return myProvider;
  }

  @NotNull
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
      assert myUrl != null;
      return myUrl;
    }
  }

  @NotNull
  public String getDescription() {
    if (myProvider != null) {
      String providerName = myProvider.getPresentableName();
      return sanitizeName(providerName);
    }

    assert myUrl != null;

    // the only weird case
    if ("http://json.schemastore.org/config".equals(myUrl)
        || "https://schemastore.azurewebsites.net/schemas/json/config.json".equals(myUrl)) {
      return "asp.net config";
    }

    String url = myUrl.replace('\\', '/');

    return ContainerUtil.reverse(StringUtil.split(url, "/"))
      .stream()
      .map(p -> sanitizeName(p))
      .filter(p -> !isVeryDumbName(p))
      .findFirst().orElse(sanitizeName(myUrl));
  }

  public static boolean isVeryDumbName(@Nullable String possibleName) {
    if (StringUtil.isEmptyOrSpaces(possibleName) || myDumbNames.contains(possibleName)) return true;
    return StringUtil.split(possibleName, ".").stream().allMatch(s -> JsonSchemaType.isInteger(s));
  }

  @NotNull
  private static String sanitizeName(@NotNull String providerName) {
    return StringUtil.trimEnd(StringUtil.trimEnd(StringUtil.trimEnd(providerName, ".json"), "-schema"), ".schema");
  }

  @NotNull
  public JsonSchemaVersion getSchemaVersion() {
    return myProvider != null ? myProvider.getSchemaVersion() : JsonSchemaVersion.SCHEMA_4;
  }

  @NotNull
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
