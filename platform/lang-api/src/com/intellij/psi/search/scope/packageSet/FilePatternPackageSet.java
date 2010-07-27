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
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;

import java.util.regex.Pattern;

public class FilePatternPackageSet implements PatternBasedPackageSet {
  public static final @NonNls String SCOPE_FILE = "file";
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
    if (modulePattern == null || modulePattern.length() == 0) {
      myModulePattern = null;
    }
    else {
      if (modulePattern.startsWith("group:")) {
        int idx = modulePattern.indexOf(':', 6);
        if (idx == -1) idx = modulePattern.length();
        myModuleGroupPattern = Pattern.compile(StringUtil.replace(modulePattern.substring(6, idx), "*", ".*"));
        if (idx < modulePattern.length() - 1) {
          myModulePattern = Pattern.compile(StringUtil.replace(modulePattern.substring(idx + 1), "*", ".*"));
        }
      } else {
        myModulePattern = Pattern.compile(StringUtil.replace(modulePattern, "*", ".*"));
      }
    }
    myFilePattern = filePattern != null ? Pattern.compile(convertToRegexp(filePattern, '/')) : null;
  }

  public boolean contains(PsiFile file, NamedScopesHolder holder) {
    Project project = file.getProject();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    VirtualFile vFile = file.getVirtualFile();
    return vFile != null && fileIndex.isInContent(vFile) && fileMatcher(vFile, fileIndex) && matchesModule(myModuleGroupPattern,
                                                                                                           myModulePattern, vFile, fileIndex);
  }

  private boolean fileMatcher(VirtualFile virtualFile, ProjectFileIndex fileIndex){
    if (myModulePattern != null) {
      final VirtualFile contentRoot = fileIndex.getContentRootForFile(virtualFile);
      return myFilePattern.matcher(VfsUtil.getRelativePath(virtualFile, contentRoot, '/')).matches();
    } else {
      return myFilePattern.matcher(getRelativePath(virtualFile, fileIndex, true)).matches();
    }
  }

  public static boolean matchesModule(final Pattern moduleGroupPattern,
                                      final Pattern modulePattern,
                                      final VirtualFile file,
                                      final ProjectFileIndex fileIndex) {
    final Module module = fileIndex.getModuleForFile(file);
    LOG.assertTrue(module != null, "url: " + file.getUrl());
    if (modulePattern != null && modulePattern.matcher(module.getName()).matches()) return true;
    final String[] groupPath = ModuleManager.getInstance(module.getProject()).getModuleGroupPath(module);
    if (groupPath != null) {
      for (String node : groupPath) {
        if (moduleGroupPattern != null && moduleGroupPattern.matcher(node).matches()) return true;
      }
    }
    return modulePattern == null && moduleGroupPattern == null;
  }


  //public for tests only
  public static String convertToRegexp(String aspectsntx, char separator) {
    StringBuffer buf = new StringBuffer(aspectsntx.length());
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
        buf.append(curChar);
      }
      cur++;
    }
    if (isAfterAsterix){
      buf.append("[^\\" + separator + "]*");
    }

    return buf.toString();
  }

  public PackageSet createCopy() {
    return new FilePatternPackageSet(myModulePatternText, myPathPattern);
  }

  public int getNodePriority() {
    return 0;
  }

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
    return Comparing.strEqual(myPathPattern, oldQName);
  }

  public static String getRelativePath(final VirtualFile virtualFile, final ProjectFileIndex index, final boolean useFQName) {
    final Module module = index.getModuleForFile(virtualFile);
    if (module != null) {
      final VirtualFile projectParent = module.getProject().getBaseDir();
      if (projectParent != null) {
        if (VfsUtil.isAncestor(projectParent, virtualFile, false)){
          final String projectRelativePath = VfsUtil.getRelativePath(virtualFile, projectParent, '/');
          return useFQName ? projectRelativePath : projectRelativePath.substring(projectRelativePath.indexOf('/') + 1);
        }
      }
      return virtualFile.getPath();
    } else {
      final VirtualFile contentRootForFile = index.getContentRootForFile(virtualFile);
      if (contentRootForFile != null) {
        return VfsUtil.getRelativePath(virtualFile, contentRootForFile, '/');
      }
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
