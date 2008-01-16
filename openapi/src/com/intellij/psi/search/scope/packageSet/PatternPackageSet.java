/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public class PatternPackageSet implements PackageSet {
  public static final @NonNls String SCOPE_TEST = "test";
  public static final @NonNls String SCOPE_SOURCE = "src";
  public static final @NonNls String SCOPE_LIBRARY = "lib";
  public static final @NonNls String SCOPE_PROBLEM = "problem";
  public static final String SCOPE_ANY = "";

  private Pattern myPattern;
  private Pattern myModulePattern;
  private Pattern myModuleGroupPattern;
  private String myAspectJSyntaxPattern;
  private String myScope;
  private String myModulePatternText;
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.search.scope.packageSet.PatternPackageSet");

  public PatternPackageSet(@Nullable String aspectPattern,
                           String scope,
                           @NonNls String modulePattern) {
    myAspectJSyntaxPattern = aspectPattern;
    myScope = scope;
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
    myPattern = aspectPattern != null ? Pattern.compile(FilePatternPackageSet.convertToRegexp(aspectPattern, '.')) : null;
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
      return fileIndex.isInContent(vFile) && FilePatternPackageSet.matchesModule(myModuleGroupPattern, myModulePattern, vFile, fileIndex);
    }
    if (myScope == SCOPE_SOURCE) {
      return isSource && !fileIndex.isInTestSourceContent(vFile) && FilePatternPackageSet.matchesModule(myModuleGroupPattern, myModulePattern, vFile, fileIndex);
    }
    if (myScope == SCOPE_LIBRARY) {
      return fileIndex.isInLibraryClasses(vFile) || fileIndex.isInLibrarySource(vFile);
    }
    if (myScope == SCOPE_TEST) {
      return isSource && fileIndex.isInTestSourceContent(vFile) && FilePatternPackageSet.matchesModule(myModuleGroupPattern, myModulePattern, vFile, fileIndex);
    }
    if (myScope == SCOPE_PROBLEM) {
      return isSource && WolfTheProblemSolver.getInstance(file.getProject()).isProblemFile(vFile) && FilePatternPackageSet.matchesModule(myModuleGroupPattern, myModulePattern, vFile, fileIndex);
    }
    throw new RuntimeException("Unknown scope: " + myScope);
  }



  private static String getPackageName(PsiFile file, ProjectFileIndex fileIndex) {
    VirtualFile vFile = file.getVirtualFile();
    if (fileIndex.isInLibrarySource(vFile)) {
      return fileIndex.getPackageNameByDirectory(vFile.getParent()) + "." + file.getVirtualFile().getNameWithoutExtension();
    }

    if (file instanceof PsiJavaFile) return ((PsiJavaFile)file).getPackageName() + "." + file.getVirtualFile().getNameWithoutExtension();
    PsiDirectory dir = file.getContainingDirectory();
    PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(dir);
    return aPackage == null ? file.getName() : aPackage.getQualifiedName() + "." + file.getVirtualFile().getNameWithoutExtension();
  }

  public PackageSet createCopy() {
    return new PatternPackageSet(myAspectJSyntaxPattern, myScope, myModulePatternText);
  }

  public int getNodePriority() {
    return 0;
  }

  public String getText() {
    StringBuffer buf = new StringBuffer();
    if (myScope != SCOPE_ANY) {
      buf.append(myScope);
    }

    if (myModulePattern != null || myModuleGroupPattern != null) {
      buf.append("[").append(myModulePatternText).append("]");
    }

    if (buf.length() > 0) {
      buf.append(':');
    }

    buf.append(myAspectJSyntaxPattern);
    return buf.toString();
  }

}