/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiPackage;

import java.util.regex.Pattern;

public class PatternPackageSet implements PackageSet {
  public static final String SCOPE_TEST = "test";
  public static final String SCOPE_SOURCE = "src";
  public static final String SCOPE_LIBRARY = "lib";
  public static final String SCOPE_ANY = "";

  private Pattern myPattern;
  private Pattern myModulePattern;
  private String myAspectJSyntaxPattern;
  private String myScope;
  private String myModulePatternText;

  public PatternPackageSet(String aspectPattern, String scope, String modulePattern) {
    myAspectJSyntaxPattern = aspectPattern;
    myScope = scope;
    myModulePatternText = modulePattern;
    myModulePattern = modulePattern == null || modulePattern.length() == 0
                      ? null
                      : Pattern.compile(StringUtil.replace(modulePattern, "*", "[^\\.]*"));
    myPattern = Pattern.compile(convertToRegexp(aspectPattern));
  }

  public boolean contains(PsiFile file, NamedScopesHolder holder) {
    Project project = file.getProject();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    return matchesScope(file, fileIndex) && myPattern.matcher(getPackageName(file, fileIndex)).matches();
  }

  private boolean matchesScope(PsiFile file, ProjectFileIndex fileIndex) {
    VirtualFile vFile = file.getVirtualFile();
    boolean isSource = fileIndex.isInSourceContent(vFile);
    if (myScope == SCOPE_ANY) return !isSource && myModulePattern == null || isSource && matchesModule(vFile, fileIndex);
    if (myScope == SCOPE_SOURCE) {
      return isSource && !fileIndex.isInTestSourceContent(vFile) && matchesModule(vFile, fileIndex);
    }
    if (myScope == SCOPE_LIBRARY) {
      return fileIndex.isInLibraryClasses(vFile) || fileIndex.isInLibrarySource(vFile);
    }

    return isSource && fileIndex.isInTestSourceContent(vFile) && matchesModule(vFile, fileIndex);
  }

  private boolean matchesModule(VirtualFile file, ProjectFileIndex fileIndex) {
    if (myModulePattern == null) return true;
    return myModulePattern.matcher(fileIndex.getModuleForFile(file).getName()).matches();
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

  private static String convertToRegexp(String aspectsntx) {
    StringBuffer buf = new StringBuffer(aspectsntx.length());
    int cur = 0;
    boolean isAfterDot = false;
    while (cur < aspectsntx.length()) {
      char curChar = aspectsntx.charAt(cur);
      if (curChar != '.' && isAfterDot) {
        buf.append("\\."); // Dot
        isAfterDot = false;
      }

      if (curChar == '*') {
        buf.append("[^\\.]*"); // Any char sequence that does not contain dots.;
      }
      else if (curChar == '.') {
        if (isAfterDot) {
          buf.append("\\.(.*\\.)?"); // Any char sequence starts and ends with dot.;
          isAfterDot = false;
        }
        else {
          isAfterDot = true;
        }
      }
      else {
        buf.append(curChar);
      }
      cur++;
    }

    return buf.toString();
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

    if (myModulePattern != null) {
      buf.append("[").append(myModulePatternText).append("]");
    }

    if (buf.length() > 0) {
      buf.append(':');
    }

    buf.append(myAspectJSyntaxPattern);
    return buf.toString();
  }
}