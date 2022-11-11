// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public class PatternPackageSet extends PatternBasedPackageSet {
  @NonNls public static final String SCOPE_TEST = "test";
  @NonNls public static final String SCOPE_SOURCE = "src";
  @NonNls public static final String SCOPE_LIBRARY = "lib";
  @NonNls public static final String SCOPE_PROBLEM = "problem";
  public static final String SCOPE_ANY = "";

  private final Pattern myPattern;
  private final String myAspectJSyntaxPattern;
  private final String myScope;

  public PatternPackageSet(@NonNls @Nullable String aspectPattern,
                           @NotNull String scope,
                           @NonNls String modulePattern) {
    super(modulePattern);
    myAspectJSyntaxPattern = aspectPattern;
    myScope = scope;
    myPattern = aspectPattern != null ? Pattern.compile(FilePatternPackageSet.convertToRegexp(aspectPattern, '.')) : null;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file, @NotNull Project project, @Nullable NamedScopesHolder holder) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (matchesScope(file, project, fileIndex)) {
      if (myPattern == null) {
        return true;
      }
      String packageName = getPackageName(file, project);
      if (packageName != null && myPattern.matcher(packageName).matches()) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesScope(VirtualFile file, Project project, ProjectFileIndex fileIndex) {
    if (file == null) return false;
    boolean isSource = fileIndex.isInSourceContent(file);
    if (myScope == SCOPE_ANY) {
      return fileIndex.isInContent(file) && matchesModule(file, fileIndex);
    }
    if (myScope == SCOPE_SOURCE) {
      return isSource && !TestSourcesFilter.isTestSources(file, project) && matchesModule(file, fileIndex);
    }
    if (myScope == SCOPE_LIBRARY) {
      return fileIndex.isInLibrary(file) && matchesLibrary(myModulePattern, file, fileIndex);
    }
    if (myScope == SCOPE_TEST) {
      return isSource && TestSourcesFilter.isTestSources(file, project) && matchesModule(file, fileIndex);
    }
    if (myScope == SCOPE_PROBLEM) {
      return isSource && WolfTheProblemSolver.getInstance(project).isProblemFile(file) && matchesModule(file, fileIndex);
    }
    throw new RuntimeException("Unknown scope: " + myScope);
  }

  private static String getPackageName(VirtualFile file, Project project) {
    VirtualFile dir = file.isDirectory() ? file : file.getParent();
    if (dir == null) return null;
    return StringUtil.getQualifiedName(PackageIndex.getInstance(project).getPackageNameByDirectory(dir), file.getNameWithoutExtension());
  }

  @NotNull
  @Override
  public PackageSet createCopy() {
    return new PatternPackageSet(myAspectJSyntaxPattern, myScope, myModulePatternText);
  }

  @Override
  public int getNodePriority() {
    return 0;
  }

  @NotNull
  @Override
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
  public boolean isOn(String oldQName) {
    return Comparing.strEqual(oldQName, myAspectJSyntaxPattern) || //class qname
           Comparing.strEqual(oldQName + "..*", myAspectJSyntaxPattern) || //package req
           Comparing.strEqual(oldQName + ".*", myAspectJSyntaxPattern); //package
  }

  @NotNull
  @Override
  public PatternBasedPackageSet updatePattern(@NotNull String oldName, @NotNull String newName) {
    return new PatternPackageSet(myAspectJSyntaxPattern.replace(oldName, newName), myScope, myModulePatternText);
  }

  @NotNull
  @Override
  public PatternBasedPackageSet updateModulePattern(@NotNull String oldName, @NotNull String newName) {
    return new PatternPackageSet(myAspectJSyntaxPattern, myScope, myModulePatternText.replace(oldName, newName));
  }

  @Override
  public String getPattern() {
    return myAspectJSyntaxPattern;
  }
}