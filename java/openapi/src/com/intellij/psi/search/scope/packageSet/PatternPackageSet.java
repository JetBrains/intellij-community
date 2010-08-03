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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public class PatternPackageSet implements PatternBasedPackageSet {
  @NonNls public static final String SCOPE_TEST = "test";
  @NonNls public static final String SCOPE_SOURCE = "src";
  @NonNls public static final String SCOPE_LIBRARY = "lib";
  @NonNls public static final String SCOPE_PROBLEM = "problem";
  public static final String SCOPE_ANY = "";

  private final Pattern myPattern;
  private final Pattern myModulePattern;
  private final Pattern myModuleGroupPattern;
  private final String myAspectJSyntaxPattern;
  private final String myScope;
  private final String myModulePatternText;

  public PatternPackageSet(@NonNls @Nullable String aspectPattern,
                           @NotNull String scope,
                           @NonNls String modulePattern) {
    myAspectJSyntaxPattern = aspectPattern;
    myScope = scope;
    myModulePatternText = modulePattern;
    Pattern mmgp = null;
    Pattern mmp = null;
    if (modulePattern == null || modulePattern.length() == 0) {
      mmp = null;
    }
    else {
      if (modulePattern.startsWith("group:")) {
        int idx = modulePattern.indexOf(':', 6);
        if (idx == -1) idx = modulePattern.length();
        mmgp = Pattern.compile(StringUtil.replace(modulePattern.substring(6, idx), "*", ".*"));
        if (idx < modulePattern.length() - 1) {
          mmp = Pattern.compile(StringUtil.replace(modulePattern.substring(idx + 1), "*", ".*"));
        }
      } else {
        mmp = Pattern.compile(StringUtil.replace(modulePattern, "*", ".*"));
      }
    }
    myModulePattern = mmp;
    myModuleGroupPattern = mmgp;
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
    VirtualFile virtualFile = file.getVirtualFile();
    if (fileIndex.isInLibrarySource(virtualFile)) {
      return fileIndex.getPackageNameByDirectory(virtualFile.getParent()) + "." + virtualFile.getNameWithoutExtension();
    }

    if (file instanceof PsiJavaFile) return ((PsiJavaFile)file).getPackageName() + "." + virtualFile.getNameWithoutExtension();
    PsiDirectory dir = file.getContainingDirectory();
    PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(dir);
    return aPackage == null ? file.getName() : aPackage.getQualifiedName() + "." + virtualFile.getNameWithoutExtension();
  }

  public PackageSet createCopy() {
    return new PatternPackageSet(myAspectJSyntaxPattern, myScope, myModulePatternText);
  }

  public int getNodePriority() {
    return 0;
  }

  public String getText() {
    StringBuilder buf = new StringBuilder();
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

  @Override
  public String getModulePattern() {
    return myModulePatternText;
  }

  @Override
  public boolean isOn(String oldQName) {
    return Comparing.strEqual(oldQName, myAspectJSyntaxPattern) || //class qname
           Comparing.strEqual(oldQName + "..*", myAspectJSyntaxPattern) || //package req
           Comparing.strEqual(oldQName + ".*", myAspectJSyntaxPattern); //package
  }

  @Override
  public String getPattern() {
    return myAspectJSyntaxPattern;
  }
}