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

/*
 * User: anna
 * Date: 15-Jan-2008
 */
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
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
import org.jetbrains.annotations.TestOnly;

import java.util.regex.Pattern;

public class FilePatternPackageSet extends PatternBasedPackageSet {
  @NonNls public static final String SCOPE_FILE = "file";
  private Pattern myModulePattern;
  private Pattern myModuleGroupPattern;
  private final String myPathPattern;
  private final Pattern myFilePattern;
  private final String myModulePatternText;
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.search.scope.packageSet.FilePatternPackageSet");

  public FilePatternPackageSet(@NonNls String modulePattern,
                               @NonNls String filePattern) {
    myPathPattern = filePattern;
    myModulePatternText = modulePattern;
    if (modulePattern == null || modulePattern.isEmpty()) {
      myModulePattern = null;
    }
    else {
      if (modulePattern.startsWith("group:")) {
        int idx = modulePattern.indexOf(':', 6);
        if (idx == -1) idx = modulePattern.length();
        myModuleGroupPattern = Pattern.compile(StringUtil.replace(escapeToRegexp(modulePattern.substring(6, idx)), "*", ".*"));
        if (idx < modulePattern.length() - 1) {
          myModulePattern = Pattern.compile(StringUtil.replace(escapeToRegexp(modulePattern.substring(idx + 1)), "*", ".*"));
        }
      } else {
        myModulePattern = Pattern.compile(StringUtil.replace(escapeToRegexp(modulePattern), "*", ".*"));
      }
    }
    myFilePattern = filePattern != null ? Pattern.compile(convertToRegexp(filePattern, '/')) : null;
  }

  @Override
  public boolean contains(VirtualFile file, @NotNull NamedScopesHolder holder) {
    return contains(file, holder.getProject(), holder);
  }

  @Override
  public boolean contains(VirtualFile file, @NotNull Project project, @Nullable NamedScopesHolder holder) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    return file != null && fileMatcher(file, fileIndex, holder != null ? holder.getProjectBaseDir() : project.getBaseDir()) &&
           matchesModule(myModuleGroupPattern, myModulePattern, file, fileIndex);
  }

  private boolean fileMatcher(@NotNull VirtualFile virtualFile, ProjectFileIndex fileIndex, VirtualFile projectBaseDir){
    final String relativePath = getRelativePath(virtualFile, fileIndex, true, projectBaseDir);
    if (relativePath == null) {
      LOG.error("vFile: " + virtualFile + "; projectBaseDir: " + projectBaseDir + "; content File: "+fileIndex.getContentRootForFile(virtualFile));
    }
    if (StringUtil.isEmptyOrSpaces(relativePath) && !virtualFile.equals(projectBaseDir)) {
      return false;
    }
    return myFilePattern.matcher(relativePath).matches();
  }

  public static boolean matchesModule(final Pattern moduleGroupPattern,
                                      final Pattern modulePattern,
                                      final VirtualFile file,
                                      final ProjectFileIndex fileIndex) {
    final Module module = fileIndex.getModuleForFile(file);
    if (module != null) {
      if (modulePattern != null && modulePattern.matcher(module.getName()).matches()) return true;
      if (moduleGroupPattern != null) {
        final String[] groupPath = ModuleManager.getInstance(module.getProject()).getModuleGroupPath(module);
        if (groupPath != null) {
          for (String node : groupPath) {
            if (moduleGroupPattern.matcher(node).matches()) return true;
          }
        }
      }
    }
    return modulePattern == null && moduleGroupPattern == null;
  }

  @NotNull
  private static String escapeToRegexp(@NotNull CharSequence text) {
    StringBuilder builder = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      final char c = text.charAt(i);
      if (c == ' ' || Character.isLetter(c) || Character.isDigit(c) || c == '_' || c == '*') {
        builder.append(c);
      }
      else {
        builder.append('\\').append(c);
      }
    }

    return builder.toString();
  }

  @TestOnly
  public static String convertToRegexp(String aspectsntx, char separator) {
    StringBuilder buf = new StringBuilder(aspectsntx.length());
    int cur = 0;
    boolean isAfterSeparator = false;
    boolean isAfterAsterix = false;
    while (cur < aspectsntx.length()) {
      char curChar = aspectsntx.charAt(cur);
      if (curChar != separator && isAfterSeparator) {
        buf.append("\\" + separator);
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
          buf.append("[^\\" + separator + "]*");
          isAfterAsterix = false;
        }
      }
      else if (curChar == separator) {
        if (isAfterSeparator) {
          buf.append("\\" +separator+ "(.*\\" + separator + ")?");
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
      buf.append("[^\\" + separator + "]*");
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
    @NonNls StringBuffer buf = new StringBuffer("file");

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
  public String getModulePattern() {
    return myModulePatternText;
  }

  @Override
  public boolean isOn(String oldQName) {
    return Comparing.strEqual(myPathPattern, oldQName) ||
           Comparing.strEqual(oldQName + "//*", myPathPattern) ||
           Comparing.strEqual(oldQName + "/*", myPathPattern);
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
