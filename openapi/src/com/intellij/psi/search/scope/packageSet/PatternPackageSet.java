/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.NonNls;

import java.util.regex.Pattern;

public class PatternPackageSet implements PackageSet {
  public static final @NonNls String SCOPE_TEST = "test";
  public static final @NonNls String SCOPE_SOURCE = "src";
  public static final @NonNls String SCOPE_LIBRARY = "lib";
  public static final @NonNls String SCOPE_FILE = "file";
  public static final @NonNls String SCOPE_PROBLEM = "problem";
  public static final String SCOPE_ANY = "";

  private Pattern myPattern;
  private Pattern myModulePattern;
  private String myAspectJSyntaxPattern;
  private String myPathPattern;
  private Pattern myFilePattern;
  private String myScope;
  private String myModulePatternText;
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.search.scope.packageSet.PatternPackageSet");

  public PatternPackageSet(String aspectPattern,
                           String scope,
                           String modulePattern,
                           @NonNls String filePattern) {
    myAspectJSyntaxPattern = aspectPattern;
    myPathPattern = filePattern;
    myScope = scope;
    myModulePatternText = modulePattern;
    myModulePattern = modulePattern == null || modulePattern.length() == 0
                      ? null
                      : Pattern.compile(StringUtil.replace(modulePattern, "*", ".*"));
    myPattern = aspectPattern != null ? Pattern.compile(convertToRegexp(aspectPattern, '.')) : null;
    if (filePattern != null){
      myFilePattern = Pattern.compile(convertToRegexp(filePattern, '/'));
    }
  }

  public boolean contains(PsiFile file, NamedScopesHolder holder) {
    Project project = file.getProject();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    return matchesScope(file, fileIndex) && (myPattern == null || myPattern.matcher(getPackageName(file, fileIndex)).matches());
  }

  private boolean matchesScope(PsiFile file, ProjectFileIndex fileIndex) {
    VirtualFile vFile = file.getVirtualFile();
    if (vFile == null) return false;
    boolean isSource = fileIndex.isInSourceContent(vFile);
    if (myScope == SCOPE_ANY) {
      return fileIndex.isInContent(vFile) && matchesModule(vFile, fileIndex);
    }
    if (myScope == SCOPE_SOURCE) {
      return isSource && !fileIndex.isInTestSourceContent(vFile) && matchesModule(vFile, fileIndex);
    }
    if (myScope == SCOPE_LIBRARY) {
      return fileIndex.isInLibraryClasses(vFile) || fileIndex.isInLibrarySource(vFile);
    }
    if (myScope == SCOPE_FILE){
      return fileIndex.isInContent(vFile) && fileMatcher(vFile, fileIndex) && matchesModule(vFile, fileIndex);
    }
    if (myScope == SCOPE_TEST) {
      return isSource && fileIndex.isInTestSourceContent(vFile) && matchesModule(vFile, fileIndex);
    }
    if (myScope == SCOPE_PROBLEM) {
      return isSource && WolfTheProblemSolver.getInstance(file.getProject()).isProblemFile(vFile) && matchesModule(vFile, fileIndex);
    }
    throw new RuntimeException("Unknown scope: " + myScope);
  }

  private boolean fileMatcher(VirtualFile virtualFile, ProjectFileIndex fileIndex){
    if (myModulePattern != null) {
      final VirtualFile contentRoot = fileIndex.isInSource(virtualFile)
                                      ? fileIndex.getSourceRootForFile(virtualFile)
                                      : fileIndex.getContentRootForFile(virtualFile);
      return myFilePattern.matcher(VfsUtil.getRelativePath(virtualFile, contentRoot, '/')).matches();
    } else {
      return myFilePattern.matcher(getRelativePath(virtualFile, fileIndex, true)).matches();
    }
  }

  private boolean matchesModule(VirtualFile file, ProjectFileIndex fileIndex) {
    if (myModulePattern == null) return true;
    final Module module = fileIndex.getModuleForFile(file);
    LOG.assertTrue(module != null);
    if (myModulePattern.matcher(module.getName()).matches()) return true;
    final String[] groupPath = ModuleManager.getInstance(module.getProject()).getModuleGroupPath(module);
    if (groupPath != null){
      for (String node : groupPath) {
        if (myModulePattern.matcher(node).matches()) return true;
      }
    }
    return false;
  }

  private static String getPackageName(PsiFile file, ProjectFileIndex fileIndex) {
    VirtualFile vFile = file.getVirtualFile();
    if (fileIndex.isInLibrarySource(vFile)) {
      return fileIndex.getPackageNameByDirectory(vFile.getParent()) + "." + file.getVirtualFile().getNameWithoutExtension();
    }

    if (file instanceof PsiJavaFile) return ((PsiJavaFile)file).getPackageName() + "." + file.getVirtualFile().getNameWithoutExtension();
    PsiDirectory dir = file.getContainingDirectory();
    PsiPackage aPackage = dir.getPackage();
    return aPackage == null ? file.getName() : aPackage.getQualifiedName() + "." + file.getVirtualFile().getNameWithoutExtension();
  }

  private static String convertToRegexp(String aspectsntx, char separator) {
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
      buf.append(".*");
    }

    return buf.toString();
  }

  public PackageSet createCopy() {
    return new PatternPackageSet(myAspectJSyntaxPattern, myScope, myModulePatternText, myPathPattern);
  }

  public int getNodePriority() {
    return 0;
  }

  public String getText() {
    StringBuffer buf = new StringBuffer();
    if (myScope != SCOPE_ANY) {
      buf.append(myScope);
    }

    if (myModulePattern != null) {
      buf.append("[").append(myModulePatternText).append("]");
    }

    if (buf.length() > 0) {
      buf.append(':');
    }

    buf.append(myAspectJSyntaxPattern != null ? myAspectJSyntaxPattern : myPathPattern);
    return buf.toString();
  }

  public static String getRelativePath(final VirtualFile virtualFile, final ProjectFileIndex index, final boolean useFQName) {
    final Module module = index.getModuleForFile(virtualFile);
    if (module != null) {
      final VirtualFile projectParent = module.getProject().getProjectFile().getParent();
      if (projectParent != null) {
        if (VfsUtil.isAncestor(projectParent, virtualFile, false)){
          final String projectRelativePath = VfsUtil.getRelativePath(virtualFile, projectParent, '/');
          return useFQName ? projectRelativePath : projectRelativePath.substring(projectRelativePath.indexOf('/') + 1);
        }
      }
      return virtualFile.getPath();
    } else {
      return VfsUtil.getRelativePath(virtualFile, index.getContentRootForFile(virtualFile), '/');
    }
  }
}