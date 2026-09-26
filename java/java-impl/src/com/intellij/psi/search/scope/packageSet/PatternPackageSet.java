// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public class PatternPackageSet extends PatternBasedPackageSet {
  public enum Scope {
    SOURCE("src"), 
    TEST("test"), 
    LIBRARY("lib"), 
    PROBLEM("problem"), 
    ANY("");
    private final String myId;

    Scope(@NotNull String id) {
      myId = id;
    }

    public String getId() {
      return myId;
    }

    public static @Nullable Scope findById(@Nullable String id) {
      return ContainerUtil.find(values(), value -> value.getId().equals(id));
    }
  }

  private final Pattern myPattern;
  private final String myAspectJSyntaxPattern;
  private final Scope myScope;

  public PatternPackageSet(@NonNls @Nullable String aspectPattern,
                           @NotNull Scope scope,
                           @NonNls String modulePattern) {
    super(modulePattern);
    myAspectJSyntaxPattern = aspectPattern;
    myScope = scope;
    myPattern = aspectPattern != null ? Pattern.compile(FilePatternPackageSet.convertToRegexp(aspectPattern, '.')) : null;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file, @NotNull Project project, @Nullable NamedScopesHolder holder) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (!matchesScope(file, project, fileIndex)) {
      return false;
    }
    if (myPattern == null) {
      return true;
    }
    String packageName = getPackageName(file, project);
    return packageName != null && myPattern.matcher(packageName).matches();
  }

  private boolean matchesScope(@NotNull VirtualFile file, @NotNull Project project, @NotNull ProjectFileIndex fileIndex) {
    boolean isSource = fileIndex.isInSourceContent(file);
    return switch (myScope) {
      case ANY -> fileIndex.isInContent(file) && matchesModule(file, fileIndex);
      case SOURCE -> isSource && !TestSourcesFilter.isTestSources(file, project) && matchesModule(file, fileIndex);
      case LIBRARY -> fileIndex.isInLibrary(file) && matchesLibrary(myModulePattern, file, fileIndex);
      case TEST -> isSource && TestSourcesFilter.isTestSources(file, project) && matchesModule(file, fileIndex);
      case PROBLEM -> isSource && WolfTheProblemSolver.getInstance(project).isProblemFile(file) && matchesModule(file, fileIndex);
    };
  }

  private static String getPackageName(@NotNull VirtualFile file, @NotNull Project project) {
    String name = PackageIndex.getInstance(project).getPackageName(file);
    return name == null ? null : StringUtil.getQualifiedName(name, file.getNameWithoutExtension());
  }

  @Override
  public @NotNull PackageSet createCopy() {
    return new PatternPackageSet(myAspectJSyntaxPattern, myScope, myModulePatternText);
  }

  @Override
  public int getNodePriority() {
    return 0;
  }

  @Override
  public @NotNull String getText() {
    StringBuilder buf = new StringBuilder();
    if (myScope != Scope.ANY) {
      buf.append(myScope.myId);
    }

    if (myModulePattern != null || myModuleGroupPattern != null) {
      buf.append("[").append(myModulePatternText).append("]");
    }

    if (!buf.isEmpty()) {
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

  @Override
  public @NotNull PatternBasedPackageSet updatePattern(@NotNull String oldName, @NotNull String newName) {
    return new PatternPackageSet(myAspectJSyntaxPattern.replace(oldName, newName), myScope, myModulePatternText);
  }

  @Override
  public @NotNull PatternBasedPackageSet updateModulePattern(@NotNull String oldName, @NotNull String newName) {
    return new PatternPackageSet(myAspectJSyntaxPattern, myScope, myModulePatternText.replace(oldName, newName));
  }

  @Override
  public String getPattern() {
    return myAspectJSyntaxPattern;
  }
}