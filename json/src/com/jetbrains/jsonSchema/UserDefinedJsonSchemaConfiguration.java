/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicClearableLazyValue;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.PatternUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @author Irina.Chernushina on 4/19/2017.
 */
@Tag("SchemaInfo")
public class UserDefinedJsonSchemaConfiguration {
  private final static Comparator<Item> ITEM_COMPARATOR = (o1, o2) -> {
    if (o1.isPattern() != o2.isPattern()) return o1.isPattern() ? -1 : 1;
    if (o1.isDirectory() != o2.isDirectory()) return o1.isDirectory() ? -1 : 1;
    return o1.path.compareToIgnoreCase(o2.path);
  };

  public String name;
  public String relativePathToSchema;
  public JsonSchemaVersion schemaVersion = JsonSchemaVersion.SCHEMA_4;
  public boolean applicationDefined;
  public List<Item> patterns = new SmartList<>();
  @Transient
  private final AtomicClearableLazyValue<List<PairProcessor<Project, VirtualFile>>> myCalculatedPatterns =
    new AtomicClearableLazyValue<List<PairProcessor<Project, VirtualFile>>>() {
      @NotNull
      @Override
      protected List<PairProcessor<Project, VirtualFile>> compute() {
        return recalculatePatterns();
      }
    };

  public UserDefinedJsonSchemaConfiguration() {
  }

  public UserDefinedJsonSchemaConfiguration(@NotNull String name,
                                            JsonSchemaVersion schemaVersion,
                                            @NotNull String relativePathToSchema,
                                            boolean applicationDefined,
                                            @Nullable List<Item> patterns) {
    this.name = name;
    this.relativePathToSchema = relativePathToSchema;
    this.schemaVersion = schemaVersion;
    this.applicationDefined = applicationDefined;
    setPatterns(patterns);
  }

  public String getName() {
    return name;
  }

  public void setName(@NotNull String name) {
    this.name = name;
  }

  public String getRelativePathToSchema() {
    return relativePathToSchema;
  }

  public JsonSchemaVersion getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(JsonSchemaVersion schemaVersion) {
    this.schemaVersion = schemaVersion;
  }

  public void setRelativePathToSchema(String relativePathToSchema) {
    this.relativePathToSchema = relativePathToSchema;
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
    Collections.sort(this.patterns, ITEM_COMPARATOR);
    myCalculatedPatterns.drop();
  }

  public void refreshPatterns() {
    myCalculatedPatterns.drop();
  }

  @NotNull
  public List<PairProcessor<Project, VirtualFile>> getCalculatedPatterns() {
    return myCalculatedPatterns.getValue();
  }

  private List<PairProcessor<Project, VirtualFile>> recalculatePatterns() {
    final List<PairProcessor<Project, VirtualFile>> result = new SmartList<>();
    for (final Item patternText : patterns) {
      switch (patternText.mappingKind) {
        case File:
          result.add((project, vfile) -> vfile.equals(getRelativeFile(project, patternText)) || vfile.getUrl().equals(patternText.path));
          break;
        case Pattern:
          String pathText = patternText.path.replace('\\', '/');
          final Pattern pattern = pathText.isEmpty()
                                  ? PatternUtil.NOTHING
                                  : pathText.indexOf('/') >= 0
                                    ? PatternUtil.compileSafe(".*" + PatternUtil.convertToRegex(pathText), PatternUtil.NOTHING)
                                    : PatternUtil.fromMask(pathText);
          result.add((project, file) -> JsonSchemaObject.matchPattern(pattern, pathText.indexOf('/') >= 0
                                                        ? file.getPath()
                                                        : file.getName()));
          break;
        case Directory:
          result.add((project, vfile) -> {
            final VirtualFile relativeFile = getRelativeFile(project, patternText);
            if (relativeFile == null || !VfsUtilCore.isAncestor(relativeFile, vfile, true)) return false;
            JsonSchemaService service = JsonSchemaService.Impl.get(project);
            return service.isApplicableToFile(vfile) && !service.isSchemaFile(vfile);
          });
          break;
      }
    }
    return result;
  }

  @Nullable
  private static VirtualFile getRelativeFile(@NotNull final Project project, @NotNull final Item pattern) {
    if (project.getBasePath() == null) {
      return null;
    }

    final String path = FileUtilRt.toSystemIndependentName(StringUtil.notNullize(pattern.path));
    final List<String> parts = pathToPartsList(path);
    if (parts.isEmpty()) {
      return project.getBaseDir();
    }
    else {
      return VfsUtil.findRelativeFile(project.getBaseDir(), ArrayUtil.toStringArray(parts));
    }
  }

  @NotNull
  private static List<String> pathToPartsList(@NotNull String path) {
    return ContainerUtil.filter(StringUtil.split(path, "/"), s -> !".".equals(s));
  }

  @NotNull
  private static String[] pathToParts(@NotNull String path) {
    return ArrayUtil.toStringArray(pathToPartsList(path));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    UserDefinedJsonSchemaConfiguration info = (UserDefinedJsonSchemaConfiguration)o;

    if (applicationDefined != info.applicationDefined) return false;
    if (schemaVersion != info.schemaVersion) return false;
    if (name != null ? !name.equals(info.name) : info.name != null) return false;
    if (relativePathToSchema != null
        ? !relativePathToSchema.equals(info.relativePathToSchema)
        : info.relativePathToSchema != null) {
      return false;
    }
    //noinspection RedundantIfStatement
    if (patterns != null ? !patterns.equals(info.patterns) : info.patterns != null) return false;

    return true;
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

  public static class Item {
    public String path;
    public JsonMappingKind mappingKind = JsonMappingKind.File;

    public Item() {
    }

    public Item(String path, JsonMappingKind mappingKind) {
      this.path = normalizePath(path);
      this.mappingKind = mappingKind;
    }

    public Item(String path, boolean isPattern, boolean isDirectory) {
      this.path = normalizePath(path);
      this.mappingKind = isPattern ? JsonMappingKind.Pattern : isDirectory ? JsonMappingKind.Directory : JsonMappingKind.File;
    }

    @NotNull
    private static String normalizePath(String path) {
      return StringUtil.trimEnd(path.replace('\\', '/').replace('/', File.separatorChar), File.separatorChar);
    }

    public String getPath() {
      return path;
    }

    public void setPath(String path) {
      this.path = normalizePath(path);
    }

    public String getError() {
      switch (mappingKind) {
        case File:
          return !StringUtil.isEmpty(path) ? null : "Empty file path doesn't match anything";
        case Pattern:
          return !StringUtil.isEmpty(path) ? null : "Empty pattern matches everything";
        case Directory:
          return null;
      }

      return "Unknown mapping kind";
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
        return mappingKind.getPrefix() + "[Project Directory]";
      }
      return mappingKind.getPrefix() + path;
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
      //noinspection RedundantIfStatement
      if (path != null ? !path.equals(item.path) : item.path != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = Objects.hashCode(path);
      result = 31 * result + Objects.hashCode(mappingKind);
      return result;
    }
  }
}
