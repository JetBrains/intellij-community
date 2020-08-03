// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.LocalFileSystem;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

public class JsonSchemaInfo {
  @Nullable private final JsonSchemaFileProvider myProvider;
  @Nullable private final String myUrl;
  @Nullable private String myName = null;
  @Nullable private String myDocumentation = null;
  @NotNull  private final static Set<String> myDumbNames = ContainerUtil.set(
    "schema",
    "lib",
    "cli",
    "packages",
    "master",
    "format",
    "angular", // the only angular-related schema is the 'angular-cli', so we skip the repo name
    "config");

  // weird cases such as meaningless 'config' as a name, etc.
  @NotNull private final static Map<String, String> myWeirdNames = ContainerUtil.stringMap(
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

    if (myWeirdNames.containsKey(myUrl)) {
      return myWeirdNames.get(myUrl);
    }

    String url = myUrl.replace('\\', '/');

    return ContainerUtil.reverse(StringUtil.split(url, "/"))
      .stream()
      .map(p -> sanitizeName(p))
      .filter(p -> !isVeryDumbName(p))
      .findFirst().orElse(sanitizeName(myUrl));
  }

  @Nullable
  public String getDocumentation() {
    return myDocumentation;
  }

  public void setDocumentation(@Nullable String documentation) {
    myDocumentation = documentation;
  }

  @Nullable
  public String getName() {
    return myName;
  }

  public void setName(@Nullable String name) {
    myName = name;
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
    if (project.isDefault() || project.getBasePath() == null || Strings.isEmptyOrSpaces(text)) {
      return text;
    }

    Path ioFile = Paths.get(text);
    if (!ioFile.isAbsolute()) {
      return text;
    }

    String relativePath = FileUtil.getRelativePath(FileUtil.toSystemIndependentName(ioFile.toString()), project.getBasePath(), '/');
    if (relativePath != null) {
      return relativePath;
    }

    VirtualFile file = LocalFileSystem.getInstance().findFileByNioFile(ioFile);
    if (file == null) {
      return text;
    }

    VirtualFile projectBaseDir = LocalFileSystem.getInstance().findFileByPath(project.getBasePath());
    if (projectBaseDir == null) {
      return text;
    }

    if (isMeaningfulAncestor(VfsUtilCore.getCommonAncestor(file, projectBaseDir))) {
      String path = VfsUtilCore.findRelativePath(projectBaseDir, file, File.separatorChar);
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
