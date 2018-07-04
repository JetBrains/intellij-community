/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.psi.search.scope.packageSet;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public class FilePatternPackageSet extends PatternBasedPackageSet {
  @NonNls public static final String SCOPE_FILE = "file";
  private final String myPathPattern;
  private final Pattern myFilePattern;
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.search.scope.packageSet.FilePatternPackageSet");

  public FilePatternPackageSet(@NonNls String modulePattern,
                               @NonNls String filePattern) {
    super(modulePattern);
    myPathPattern = filePattern;
    myFilePattern = filePattern != null ? Pattern.compile(convertToRegexp(filePattern, '/')) : null;
  }

  @Override
  public boolean contains(VirtualFile file, @NotNull NamedScopesHolder holder) {
    return contains(file, holder.getProject(), holder);
  }

  @Override
  public boolean contains(VirtualFile file, @NotNull Project project, @Nullable NamedScopesHolder holder) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    return file != null && fileMatcher(file, fileIndex, holder != null ? holder.getProjectBaseDir() : project.getBaseDir())
           && matchesModule(file, fileIndex);
  }

  private boolean fileMatcher(@NotNull VirtualFile virtualFile, ProjectFileIndex fileIndex, VirtualFile projectBaseDir){
    if (virtualFile instanceof VirtualFileWindow) {
      virtualFile = ((VirtualFileWindow)virtualFile).getDelegate();
    }
    final String relativePath = getRelativePath(virtualFile, fileIndex, true, projectBaseDir);
    if (relativePath == null) {
      LOG.error("vFile: " + virtualFile + "; projectBaseDir: " + projectBaseDir + "; content File: "+fileIndex.getContentRootForFile(virtualFile));
    }
    if (StringUtil.isEmptyOrSpaces(relativePath) && !virtualFile.equals(projectBaseDir)) {
      return false;
    }
    return myFilePattern.matcher(relativePath).matches();
  }

  static String convertToRegexp(String aspectsntx, char separator) {
    StringBuilder buf = new StringBuilder(aspectsntx.length());
    int cur = 0;
    boolean isAfterSeparator = false;
    boolean isAfterAsterix = false;
    while (cur < aspectsntx.length()) {
      char curChar = aspectsntx.charAt(cur);
      if (curChar != separator && isAfterSeparator) {
        buf.append("\\").append(separator);
        isAfterSeparator = false;
      }

      if (curChar != '*' && isAfterAsterix) {
        buf.append(".*");
        isAfterAsterix = false;
      }

      if (curChar == '*') {
        if (!isAfterAsterix){
          isAfterAsterix = true;
        } else {
          buf.append("[^\\").append(separator).append("]*");
          isAfterAsterix = false;
        }
      }
      else if (curChar == separator) {
        if (isAfterSeparator) {
          buf.append("\\").append(separator).append("(.*\\").append(separator).append(")?");
          isAfterSeparator = false;
        }
        else {
          isAfterSeparator = true;
        }
      }
      else {
        if (curChar == '.') {
          buf.append("\\");
        }
        buf.append(curChar);
      }
      cur++;
    }
    if (isAfterAsterix){
      buf.append("[^\\").append(separator).append("]*");
    }

    return buf.toString();
  }

  @Override
  @NotNull
  public PackageSet createCopy() {
    return new FilePatternPackageSet(myModulePatternText, myPathPattern);
  }

  @Override
  public int getNodePriority() {
    return 0;
  }

  @Override
  @NotNull
  public String getText() {
    @NonNls StringBuilder buf = new StringBuilder("file");

    if (myModulePattern != null || myModuleGroupPattern != null) {
      buf.append("[").append(myModulePatternText).append("]");
    }

    if (buf.length() > 0) {
      buf.append(':');
    }

    buf.append(myPathPattern);
    return buf.toString();
  }

  @Override
  public String getPattern() {
    return myPathPattern;
  }

  @Override
  public boolean isOn(String oldQName) {
    return Comparing.strEqual(myPathPattern, oldQName) ||
           Comparing.strEqual(oldQName + "//*", myPathPattern) ||
           Comparing.strEqual(oldQName + "/*", myPathPattern);
  }

  @NotNull
  @Override
  public PatternBasedPackageSet updatePattern(@NotNull String oldName, @NotNull String newName) {
    return new FilePatternPackageSet(myModulePatternText, myPathPattern.replace(oldName, newName));
  }

  @NotNull
  @Override
  public PatternBasedPackageSet updateModulePattern(@NotNull String oldName, @NotNull String newName) {
    return new FilePatternPackageSet(myModulePatternText.replace(oldName, newName), myPathPattern);
  }

  @Nullable
  public static String getRelativePath(@NotNull VirtualFile virtualFile,
                                       @NotNull ProjectFileIndex index,
                                       final boolean useFQName,
                                       VirtualFile projectBaseDir) {
    final VirtualFile contentRootForFile = index.getContentRootForFile(virtualFile);
    if (contentRootForFile != null) {
      return VfsUtilCore.getRelativePath(virtualFile, contentRootForFile, '/');
    }
    final Module module = index.getModuleForFile(virtualFile);
    if (module != null) {
      if (projectBaseDir != null) {
        if (VfsUtilCore.isAncestor(projectBaseDir, virtualFile, false)){
          final String projectRelativePath = VfsUtilCore.getRelativePath(virtualFile, projectBaseDir, '/');
          return useFQName ? projectRelativePath : projectRelativePath.substring(projectRelativePath.indexOf('/') + 1);
        }
      }
      return virtualFile.getPath();
    } else {
      return getLibRelativePath(virtualFile, index);
    }
  }

  public static String getLibRelativePath(final VirtualFile virtualFile, final ProjectFileIndex index) {
    StringBuilder relativePath = new StringBuilder(100);
    VirtualFile directory = virtualFile;
    while (directory != null && index.isInLibraryClasses(directory)) {
      relativePath.insert(0, '/');
      relativePath.insert(0, directory.getName());
      directory = directory.getParent();
    }
    return relativePath.toString();
  }
}
