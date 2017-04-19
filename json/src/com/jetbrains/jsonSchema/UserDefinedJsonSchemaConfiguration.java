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
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PatternUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;

/**
 * @author Irina.Chernushina on 4/19/2017.
 */
public class UserDefinedJsonSchemaConfiguration {
  private final static Comparator<Item> ITEM_COMPARATOR = (o1, o2) -> {
    if (o1.isPattern() != o2.isPattern()) return o1.isPattern() ? -1 : 1;
    if (o1.isDirectory() != o2.isDirectory()) return o1.isDirectory() ? -1 : 1;
    return o1.getPath().compareToIgnoreCase(o2.getPath());
  };

  private String myName;
  private String myRelativePathToSchema;
  private boolean myApplicationLevel;
  private List<Item> myPatterns = new ArrayList<>();
  @Transient
  private List<Processor<VirtualFile>> myCalculatedPatterns;

  public UserDefinedJsonSchemaConfiguration() {
  }

  public UserDefinedJsonSchemaConfiguration(@NotNull String name, @NotNull String relativePathToSchema,
                                            boolean applicationLevel,
                                            List<Item> patterns) {
    myName = name;
    myRelativePathToSchema = relativePathToSchema;
    myApplicationLevel = applicationLevel;
    myPatterns = new ArrayList<>();
    if (patterns != null) {
      myPatterns.addAll(patterns);
    }
    Collections.sort(myPatterns, ITEM_COMPARATOR);
  }

  public String getName() {
    return myName;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }

  public String getRelativePathToSchema() {
    return myRelativePathToSchema;
  }

  public void setRelativePathToSchema(String relativePathToSchema) {
    myRelativePathToSchema = relativePathToSchema;
  }

  public boolean isApplicationLevel() {
    return myApplicationLevel;
  }

  public void setApplicationLevel(boolean applicationLevel) {
    myApplicationLevel = applicationLevel;
  }

  public List<Item> getPatterns() {
    return myPatterns;
  }

  public void setPatterns(List<Item> patterns) {
    myPatterns = patterns;
    myCalculatedPatterns = null;
  }

  @NotNull
  public List<Processor<VirtualFile>> getCalculatedPatterns(@NotNull final Project project) {
    if (myCalculatedPatterns == null) recalculatePatterns(project);
    return myCalculatedPatterns;
  }

  private void recalculatePatterns(Project project) {
    myCalculatedPatterns = new ArrayList<>();
    for (final Item pattern : myPatterns) {
      if (pattern.isPattern()) {
        myCalculatedPatterns.add(new Processor<VirtualFile>() {
          private Matcher matcher = PatternUtil.fromMask(pattern.getPath()).matcher("");

          @Override
          public boolean process(VirtualFile file) {
            matcher.reset(file.getName());
            return matcher.matches();
          }
        });
      }
      else {
        if (project == null || project.getBasePath() == null) {
          continue;
        }

        final String path = FileUtilRt.toSystemIndependentName(pattern.getPath());
        final List<String> parts = ContainerUtil.filter(StringUtil.split(path, "/"), s -> !".".equals(s));
        final VirtualFile relativeFile;
        if (parts.isEmpty()) {
          relativeFile = project.getBaseDir();
        }
        else {
          relativeFile = VfsUtil.findRelativeFile(project.getBaseDir(), ArrayUtil.toStringArray(parts));
          if (relativeFile == null) continue;
        }

        if (pattern.isDirectory()) {
          myCalculatedPatterns.add(file12 -> VfsUtilCore.isAncestor(relativeFile, file12, true));
        }
        else {
          myCalculatedPatterns.add(relativeFile::equals);
        }
      }
    }
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    UserDefinedJsonSchemaConfiguration info = (UserDefinedJsonSchemaConfiguration)o;

    if (myApplicationLevel != info.myApplicationLevel) return false;
    if (myName != null ? !myName.equals(info.myName) : info.myName != null) return false;
    if (myRelativePathToSchema != null
        ? !myRelativePathToSchema.equals(info.myRelativePathToSchema)
        : info.myRelativePathToSchema != null) {
      return false;
    }
    if (myPatterns != null ? !myPatterns.equals(info.myPatterns) : info.myPatterns != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myName != null ? myName.hashCode() : 0;
    result = 31 * result + (myRelativePathToSchema != null ? myRelativePathToSchema.hashCode() : 0);
    result = 31 * result + (myApplicationLevel ? 1 : 0);
    result = 31 * result + (myPatterns != null ? myPatterns.hashCode() : 0);
    return result;
  }

  public static class Item {
    private String myPath;
    private boolean myIsPattern;
    private boolean myIsDirectory;

    public Item() {
    }

    public Item(String path, boolean isPattern, boolean isDirectory) {
      myPath = path;
      myIsPattern = isPattern;
      myIsDirectory = isDirectory;
    }

    public String getPath() {
      return myPath;
    }

    public void setPath(String path) {
      myPath = path;
    }

    public boolean isPattern() {
      return myIsPattern;
    }

    public void setPattern(boolean pattern) {
      myIsPattern = pattern;
    }

    public boolean isDirectory() {
      return myIsDirectory;
    }

    public void setDirectory(boolean directory) {
      myIsDirectory = directory;
    }

    public String getPresentation() {
      final String prefix = myIsPattern ? "Pattern: " : (myIsDirectory ? "Directory: " : "File: ");
      return prefix + myPath;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Item item = (Item)o;

      if (myIsPattern != item.myIsPattern) return false;
      if (myIsDirectory != item.myIsDirectory) return false;
      if (myPath != null ? !myPath.equals(item.myPath) : item.myPath != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myPath != null ? myPath.hashCode() : 0;
      result = 31 * result + (myIsPattern ? 1 : 0);
      result = 31 * result + (myIsDirectory ? 1 : 0);
      return result;
    }
  }
}
