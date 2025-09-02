// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema;

import com.intellij.json.JsonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.Tooltip;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.PairProcessor;
import com.intellij.util.PatternUtil;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils;
import com.jetbrains.jsonSchema.remote.JsonFileResolver;
import com.jetbrains.jsonSchema.settings.mappings.JsonSchemaVersionConverter;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Tag("SchemaInfo")
public final class UserDefinedJsonSchemaConfiguration {
  private static final Comparator<Item> ITEM_COMPARATOR = (o1, o2) -> {
    if (o1.isPattern() != o2.isPattern()) return o1.isPattern() ? -1 : 1;
    if (o1.isDirectory() != o2.isDirectory()) return o1.isDirectory() ? -1 : 1;
    return o1.path.compareToIgnoreCase(o2.path);
  };

  private @Nls String name;
  private @Nullable @Nls String generatedName;
  public String relativePathToSchema;
  @OptionTag(converter = JsonSchemaVersionConverter.class)
  public @NotNull JsonSchemaVersion schemaVersion = JsonSchemaVersion.SCHEMA_4;
  public boolean applicationDefined;
  public List<Item> patterns = new SmartList<>();
  public boolean isIgnoredFile = false;
  @Transient
  private final SynchronizedClearableLazy<List<PairProcessor<Project, VirtualFile>>> myCalculatedPatterns = new SynchronizedClearableLazy<>(
    this::recalculatePatterns);

  public UserDefinedJsonSchemaConfiguration() {
  }

  public UserDefinedJsonSchemaConfiguration(@NotNull @NlsSafe String name,
                                            @Nullable JsonSchemaVersion schemaVersion,
                                            @NotNull String relativePathToSchema,
                                            boolean applicationDefined,
                                            @Nullable List<Item> patterns) {
    this.name = name;
    this.relativePathToSchema = relativePathToSchema;
    this.schemaVersion = schemaVersion == null ? JsonSchemaVersion.SCHEMA_4 : schemaVersion;
    this.applicationDefined = applicationDefined;
    setPatterns(patterns);
  }

  public void setGeneratedName(@NotNull @Nls String generatedName) {
    this.generatedName = generatedName;
  }

  public @Nls String getGeneratedName() {
    return this.generatedName;
  }

  public @Nls String getName() {
    return name;
  }

  public void setName(@NotNull @Nls String name) {
    this.name = name;
  }

  public boolean isIgnoredFile() {
    return isIgnoredFile;
  }

  public void setIgnoredFile(boolean ignoredFile) {
    isIgnoredFile = ignoredFile;
  }

  public String getRelativePathToSchema() {
    return Item.normalizePath(relativePathToSchema);
  }

  public @NotNull JsonSchemaVersion getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(JsonSchemaVersion schemaVersion) {
    this.schemaVersion = schemaVersion == null ? JsonSchemaVersion.SCHEMA_4 : schemaVersion;
  }

  public void setRelativePathToSchema(String relativePathToSchema) {
    this.relativePathToSchema = Item.neutralizePath(relativePathToSchema);
  }

  public boolean isApplicationDefined() {
    return applicationDefined;
  }

  public void setApplicationDefined(boolean applicationDefined) {
    this.applicationDefined = applicationDefined;
  }

  public List<Item> getPatterns() {
    return patterns;
  }

  public void setPatterns(@Nullable List<Item> patterns) {
    this.patterns.clear();
    if (patterns != null) this.patterns.addAll(patterns);
    this.patterns.sort(ITEM_COMPARATOR);
    myCalculatedPatterns.drop();
  }

  public void refreshPatterns() {
    myCalculatedPatterns.drop();
  }

  public @NotNull List<PairProcessor<Project, VirtualFile>> getCalculatedPatterns() {
    return myCalculatedPatterns.getValue();
  }

  private List<PairProcessor<Project, VirtualFile>> recalculatePatterns() {
    final List<PairProcessor<Project, VirtualFile>> result = new SmartList<>();
    for (final Item patternText : patterns) {
      switch (patternText.mappingKind) {
        case File -> result.add((project, vfile) -> vfile.equals(getRelativeFile(project, patternText)) ||
                                                    vfile.getUrl().equals(Item.neutralizePath(patternText.getPath())));
        case Pattern -> {
          String pathText = FileUtil.toSystemIndependentName(patternText.getPath());
          final Pattern pattern = pathText.isEmpty()
                                  ? PatternUtil.NOTHING
                                  : pathText.indexOf('/') >= 0
                                    ? PatternUtil.compileSafe(".*/" + PatternUtil.convertToRegex(pathText), PatternUtil.NOTHING)
                                    : PatternUtil.fromMask(pathText);
          result.add((project, file) -> JsonSchemaObjectReadingUtils.matchPattern(pattern, pathText.indexOf('/') >= 0
                                                                               ? file.getPath()
                                                                               : file.getName()));
        }
        case Directory -> result.add((project, vfile) -> {
          final VirtualFile relativeFile = getRelativeFile(project, patternText);
          if (relativeFile == null || !VfsUtilCore.isAncestor(relativeFile, vfile, true)) return false;
          JsonSchemaService service = JsonSchemaService.Impl.get(project);
          return service.isApplicableToFile(vfile);
        });
      }
    }
    return result;
  }

  private static @Nullable VirtualFile getRelativeFile(final @NotNull Project project, final @NotNull Item pattern) {
    if (project.getBasePath() == null) {
      return null;
    }

    final String path = FileUtilRt.toSystemIndependentName(StringUtil.notNullize(pattern.path));
    final List<String> parts = pathToPartsList(path);
    if (parts.isEmpty()) {
      return project.getBaseDir();
    }
    else {
      return VfsUtil.findRelativeFile(project.getBaseDir(), ArrayUtilRt.toStringArray(parts));
    }
  }

  private static @NotNull List<String> pathToPartsList(@NotNull String path) {
    return ContainerUtil.filter(StringUtil.split(path, "/"), s -> !".".equals(s));
  }

  private static String @NotNull [] pathToParts(@NotNull String path) {
    return ArrayUtilRt.toStringArray(pathToPartsList(path));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    UserDefinedJsonSchemaConfiguration info = (UserDefinedJsonSchemaConfiguration)o;

    if (applicationDefined != info.applicationDefined) return false;
    if (schemaVersion != info.schemaVersion) return false;
    if (!Objects.equals(name, info.name)) return false;
    if (!Objects.equals(relativePathToSchema, info.relativePathToSchema)) return false;

    return Objects.equals(patterns, info.patterns);
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (relativePathToSchema != null ? relativePathToSchema.hashCode() : 0);
    result = 31 * result + (applicationDefined ? 1 : 0);
    result = 31 * result + (patterns != null ? patterns.hashCode() : 0);
    result = 31 * result + schemaVersion.hashCode();
    return result;
  }


  public static final class Item {
    public String path;
    public JsonMappingKind mappingKind = JsonMappingKind.File;

    public Item() {
    }

    public Item(String path, JsonMappingKind mappingKind) {
      this.path = neutralizePath(path);
      this.mappingKind = mappingKind;
    }

    public Item(String path, boolean isPattern, boolean isDirectory) {
      this.path = neutralizePath(path);
      this.mappingKind = isPattern ? JsonMappingKind.Pattern : isDirectory ? JsonMappingKind.Directory : JsonMappingKind.File;
    }

    private static @NotNull String normalizePath(@NotNull String path) {
      if (preserveSlashes(path)) return path;
      return StringUtil.trimEnd(FileUtilRt.toSystemDependentName(path), File.separatorChar);
    }

    private static boolean preserveSlashes(@NotNull String path) {
      // http/https URLs to schemas
      // mock URLs of fragments editor
      return StringUtil.startsWith(path, "http:")
             || StringUtil.startsWith(path, "https:")
             || JsonFileResolver.isTempOrMockUrl(path);
    }

    public static @NotNull String neutralizePath(@NotNull String path) {
      if (preserveSlashes(path)) return path;
      return StringUtil.trimEnd(FileUtilRt.toSystemIndependentName(path), '/');
    }

    public String getPath() {
      return normalizePath(path);
    }

    public void setPath(String path) {
      this.path = neutralizePath(path);
    }

    public @Tooltip String getError() {
      return switch (mappingKind) {
        case File -> !StringUtil.isEmpty(path) ? null : JsonBundle.message("schema.configuration.error.empty.file.path");
        case Pattern -> !StringUtil.isEmpty(path) ? null : JsonBundle.message("schema.configuration.error.empty.pattern");
        case Directory -> null;
      };
    }

    public boolean isPattern() {
      return mappingKind == JsonMappingKind.Pattern;
    }

    public void setPattern(boolean pattern) {
      mappingKind = pattern ? JsonMappingKind.Pattern : JsonMappingKind.File;
    }

    public boolean isDirectory() {
      return mappingKind == JsonMappingKind.Directory;
    }

    public void setDirectory(boolean directory) {
      mappingKind = directory ? JsonMappingKind.Directory : JsonMappingKind.File;
    }

    public String getPresentation() {
      if (mappingKind == JsonMappingKind.Directory && StringUtil.isEmpty(path)) {
        return JsonBundle.message("schema.configuration.project.directory", mappingKind.getPrefix());
      }
      return mappingKind.getPrefix() + getPath();
    }

    public String[] getPathParts() {
      return pathToParts(path);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Item item = (Item)o;

      if (mappingKind != item.mappingKind) return false;
      return Objects.equals(path, item.path);
    }

    @Override
    public int hashCode() {
      int result = Objects.hashCode(path);
      result = 31 * result + Objects.hashCode(mappingKind);
      return result;
    }
  }
}
