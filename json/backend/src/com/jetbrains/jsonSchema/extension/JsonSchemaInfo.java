// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.impl.JsonSchemaType;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

public class JsonSchemaInfo {
  private final @Nullable JsonSchemaFileProvider myProvider;
  private final @Nullable @NlsSafe String myUrl;
  private @Nullable @Nls String myName = null;
  private @Nullable @Nls String myDocumentation = null;
  private static final @NotNull Set<String> myDumbNames = Set.of(
    "schema",
    "lib",
    "cli",
    "packages",
    "master",
    "format",
    "angular", // the only angular-related schema is the 'angular-cli', so we skip the repo name
    "config");

  // Strange cases such as meaningless 'config' as a name, etc.
  private static final @NotNull Map<String, @NlsSafe String> myWeirdNames = Map.of(
    "http://json.schemastore.org/config", "asp.net config",
    "https://schemastore.azurewebsites.net/schemas/json/config.json", "asp.net config",
    "http://json.schemastore.org/2.0.0-csd.2.beta.2018-10-10.json", "sarif-2.0.0-csd.2.beta.2018-10-10",
    "https://schemastore.azurewebsites.net/schemas/json/2.0.0-csd.2.beta.2018-10-10.json", "sarif-2.0.0-csd.2.beta.2018-10-10"
  );

  public JsonSchemaInfo(@NotNull JsonSchemaFileProvider provider) {
    myProvider = provider;
    myUrl = null;
  }

  public JsonSchemaInfo(@NotNull String url) {
    myUrl = url;
    myProvider = null;
  }

  public @Nullable JsonSchemaFileProvider getProvider() {
    return myProvider;
  }

  public @NotNull String getUrl(Project project) {
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

      if (schemaFile.getFileSystem() instanceof JarFileSystem) {
        return schemaFile.getUrl();
      }

      return getRelativePath(project, schemaFile.getPath());
    }
    else {
      assert myUrl != null;
      return myUrl;
    }
  }

  public @NotNull @Nls String getDescription() {
    if (myProvider != null) {
      String providerName = myProvider.getPresentableName();
      return sanitizeName(providerName);
    }

    if (getName() != null) {
      return getName();
    }

    assert myUrl != null;

    if (myWeirdNames.containsKey(myUrl)) {
      return myWeirdNames.get(myUrl);
    }

    String url = myUrl.replace('\\', '/');

    return ContainerUtil.reverse(StringUtil.split(url, "/"))
      .stream()
      .filter(p -> !isVeryDumbName(p))
      .findFirst().orElse(myUrl);
  }

  public @Nullable @Nls String getDocumentation() {
    return myDocumentation;
  }

  public void setDocumentation(@Nullable @Nls String documentation) {
    myDocumentation = documentation;
  }

  public @Nullable @Nls String getName() {
    return myName;
  }

  public void setName(@Nullable @Nls String name) {
    myName = name;
  }

  public static boolean isVeryDumbName(@Nullable String possibleName) {
    if (StringUtil.isEmptyOrSpaces(possibleName) || myDumbNames.contains(possibleName)) return true;
    return StringUtil.split(possibleName, ".").stream().allMatch(s -> JsonSchemaType.isInteger(s));
  }

  private static @NotNull @NlsSafe String sanitizeName(@NotNull String providerName) {
    return StringUtil.trimEnd(StringUtil.trimEnd(StringUtil.trimEnd(providerName, ".json"), "-schema"), ".schema");
  }

  public @NotNull JsonSchemaVersion getSchemaVersion() {
    return myProvider != null ? myProvider.getSchemaVersion() : JsonSchemaVersion.SCHEMA_4;
  }

  public static @NotNull String getRelativePath(@NotNull Project project, @NotNull String text) {
    text = text.trim();
    if (project.isDefault() || project.getBasePath() == null || Strings.isEmptyOrSpaces(text)) {
      return text;
    }

    Path file = Paths.get(text);
    if (!file.isAbsolute()) {
      return text;
    }

    String relativePath = FileUtil.getRelativePath(project.getBasePath(), FileUtil.toSystemIndependentName(file.toString()), '/');
    if (relativePath != null) {
      return relativePath;
    }

    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByNioFile(file);
    if (virtualFile == null) {
      return text;
    }

    VirtualFile projectBaseDir = LocalFileSystem.getInstance().findFileByPath(project.getBasePath());
    if (projectBaseDir == null) {
      return text;
    }

    if (isMeaningfulAncestor(VfsUtilCore.getCommonAncestor(virtualFile, projectBaseDir))) {
      String path = VfsUtilCore.findRelativePath(projectBaseDir, virtualFile, File.separatorChar);
      if (path != null) {
        return path;
      }
    }
    return text;
  }

  private static boolean isMeaningfulAncestor(@Nullable VirtualFile ancestor) {
    if (ancestor == null) return false;
    VirtualFile homeDir = VfsUtil.getUserHomeDir();
    return homeDir != null && VfsUtilCore.isAncestor(homeDir, ancestor, true);
  }
}
