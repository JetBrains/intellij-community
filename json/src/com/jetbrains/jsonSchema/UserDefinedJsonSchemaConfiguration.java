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
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Irina.Chernushina on 4/19/2017.
 */
@Tag("SchemaInfo")
public class UserDefinedJsonSchemaConfiguration {
  private final static Comparator<Item> ITEM_COMPARATOR = (o1, o2) -> {
    if (o1.pattern != o2.pattern) return o1.pattern ? -1 : 1;
    if (o1.directory != o2.directory) return o1.directory ? -1 : 1;
    return o1.path.compareToIgnoreCase(o2.path);
  };

  public String name;
  public String relativePathToSchema;
  public boolean applicationLevel;
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

  public UserDefinedJsonSchemaConfiguration(@NotNull String name, @NotNull String relativePathToSchema,
                                            boolean applicationLevel, @Nullable List<Item> patterns) {
    this.name = name;
    this.relativePathToSchema = relativePathToSchema;
    this.applicationLevel = applicationLevel;
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

  public void setRelativePathToSchema(String relativePathToSchema) {
    this.relativePathToSchema = relativePathToSchema;
  }

  public boolean isApplicationLevel() {
    return applicationLevel;
  }

  public void setApplicationLevel(boolean applicationLevel) {
    this.applicationLevel = applicationLevel;
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

  @NotNull
  public List<PairProcessor<Project, VirtualFile>> getCalculatedPatterns() {
    return myCalculatedPatterns.getValue();
  }

  private List<PairProcessor<Project, VirtualFile>> recalculatePatterns() {
    final List<PairProcessor<Project, VirtualFile>> result = new SmartList<>();
    for (final Item patternText : patterns) {
      if (patternText.pattern) {
        result.add(new PairProcessor<Project, VirtualFile>() {
          private final Pattern pattern = PatternUtil.fromMask(patternText.path);

          @Override
          public boolean process(Project project, VirtualFile file) {
            return JsonSchemaObject.matchPattern(pattern, file.getName());
          }
        });
      }
      else if (patternText.directory) {
        result.add((project, vfile) -> {
          final VirtualFile relativeFile = getRelativeFile(project, patternText);
          return relativeFile != null && VfsUtilCore.isAncestor(relativeFile, vfile, true);
        });
      }
      else {
        result.add((project, vfile) -> vfile.equals(getRelativeFile(project, patternText)));
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
    final List<String> parts = ContainerUtil.filter(StringUtil.split(path, "/"), s -> !".".equals(s));
    if (parts.isEmpty()) {
      return project.getBaseDir();
    }
    else {
      return VfsUtil.findRelativeFile(project.getBaseDir(), ArrayUtil.toStringArray(parts));
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    UserDefinedJsonSchemaConfiguration info = (UserDefinedJsonSchemaConfiguration)o;

    if (applicationLevel != info.applicationLevel) return false;
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
    result = 31 * result + (applicationLevel ? 1 : 0);
    result = 31 * result + (patterns != null ? patterns.hashCode() : 0);
    return result;
  }

  public static class Item {
    public String path;
    public boolean pattern;
    public boolean directory;

    public Item() {
    }

    public Item(String path, boolean isPattern, boolean isDirectory) {
      this.path = path;
      pattern = isPattern;
      directory = isDirectory;
    }

    public String getPath() {
      return path;
    }

    public void setPath(String path) {
      this.path = path;
    }

    public boolean isPattern() {
      return pattern;
    }

    public void setPattern(boolean pattern) {
      this.pattern = pattern;
    }

    public boolean isDirectory() {
      return directory;
    }

    public void setDirectory(boolean directory) {
      this.directory = directory;
    }

    public String getPresentation() {
      final String prefix = pattern ? "Pattern: " : (directory ? "Directory: " : "File: ");
      return prefix + path;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Item item = (Item)o;

      if (pattern != item.pattern) return false;
      if (directory != item.directory) return false;
      //noinspection RedundantIfStatement
      if (path != null ? !path.equals(item.path) : item.path != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = path != null ? path.hashCode() : 0;
      result = 31 * result + (pattern ? 1 : 0);
      result = 31 * result + (directory ? 1 : 0);
      return result;
    }
  }
}
