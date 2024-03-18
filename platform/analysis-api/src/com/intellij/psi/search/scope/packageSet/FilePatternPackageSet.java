// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class FilePatternPackageSet extends PatternBasedPackageSet {
  private static final Logger LOG = Logger.getInstance(FilePatternPackageSet.class);

  public static final @NonNls String SCOPE_FILE = "file";
  public static final @NonNls String SCOPE_EXT = "ext";
  private final String myPathPattern;
  private final Pattern myFilePattern;
  private final boolean myProjectFiles;

  public FilePatternPackageSet(@NonNls String modulePattern,
                               @NonNls String filePattern) {
    this(modulePattern, filePattern, true);
  }

  public FilePatternPackageSet(@NonNls String modulePattern,
                               @NonNls String filePattern,
                               boolean projectFiles) {
    super(modulePattern);
    myPathPattern = filePattern;
    myFilePattern = filePattern != null ? Pattern.compile(convertToRegexp(filePattern, '/')) : null;
    myProjectFiles = projectFiles;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file, @NotNull Project project, @Nullable NamedScopesHolder holder) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    return fileMatcher(file, fileIndex, holder != null ? holder.getProjectBaseDir() : project.getBaseDir()) &&
           (myProjectFiles ? matchesModule(file, fileIndex) : matchesLibrary(myModulePattern, file, fileIndex));
  }

  private boolean fileMatcher(@NotNull VirtualFile virtualFile, ProjectFileIndex fileIndex, VirtualFile projectBaseDir){
    if (virtualFile instanceof VirtualFileWindow) {
      virtualFile = ((VirtualFileWindow)virtualFile).getDelegate();
    }
    
    if (fileIndex.isInContent(virtualFile) != myProjectFiles) return false;
    
    String relativePath = getRelativePath(virtualFile, fileIndex, true, projectBaseDir);
    if (relativePath == null) {
      LOG.error("vFile: " + virtualFile + "; projectBaseDir: " + projectBaseDir + "; content File: "+fileIndex.getContentRootForFile(virtualFile));
    }
    if (StringUtil.isEmptyOrSpaces(relativePath) && !virtualFile.equals(projectBaseDir)) {
      return false;
    }
    if (virtualFile.isDirectory()) relativePath += '/';
    return myFilePattern.matcher(relativePath).matches();
  }

  public static String convertToRegexp(String aspectsntx, char separator) {
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
  public @NotNull PackageSet createCopy() {
    return new FilePatternPackageSet(myModulePatternText, myPathPattern, myProjectFiles);
  }

  @Override
  public int getNodePriority() {
    return 0;
  }

  @Override
  public @NotNull String getText() {
    @NonNls StringBuilder buf = new StringBuilder(myProjectFiles ? SCOPE_FILE : SCOPE_EXT);

    if (myModulePattern != null || myModuleGroupPattern != null) {
      buf.append("[").append(myModulePatternText).append("]");
    }

    buf.append(':');

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

  @Override
  public @NotNull PatternBasedPackageSet updatePattern(@NotNull String oldName, @NotNull String newName) {
    return new FilePatternPackageSet(myModulePatternText, myPathPattern.replace(oldName, newName), myProjectFiles);
  }

  @Override
  public @NotNull PatternBasedPackageSet updateModulePattern(@NotNull String oldName, @NotNull String newName) {
    return new FilePatternPackageSet(myModulePatternText.replace(oldName, newName), myPathPattern, myProjectFiles);
  }

  public static @Nullable String getRelativePath(@NotNull VirtualFile virtualFile,
                                                 @NotNull ProjectFileIndex index,
                                                 final boolean useFQName,
                                                 VirtualFile projectBaseDir) {
    final VirtualFile contentRootForFile = index.getContentRootForFile(virtualFile);
    if (contentRootForFile != null) {
      String relativePath = VfsUtilCore.getRelativePath(virtualFile, contentRootForFile, '/');
      if (relativePath != null) {
        return relativePath;
      }

      if (!virtualFile.getFileSystem().equals(contentRootForFile.getFileSystem())) {
        VirtualFile parent = virtualFile.getParent();
        String relativeToParent = parent != null ? VfsUtilCore.getRelativePath(parent, contentRootForFile, '/') : null;
        if (relativeToParent != null) {
          return relativeToParent + '/' + virtualFile.getName();
        }
      }
      return null;
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
    List<String> path = new ArrayList<>();
    VirtualFile directory = virtualFile;
    while (directory != null && index.isInLibrary(directory)) {
      path.add(directory.getName());
      directory = directory.getParent();
    }
    if (path.isEmpty()) return "";
    Collections.reverse(path);
    return StringUtil.join(ArrayUtilRt.toStringArray(path), 1, path.size(), "/");
  }
}
