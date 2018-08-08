// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.ide.scratch.RootType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.HiddenFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FindSymbolParameters {
  private final String myCompletePattern;
  private final String myLocalPatternName;
  private final GlobalSearchScope mySearchScope;
  private final IdFilter myIdFilter;

  public FindSymbolParameters(@NotNull String pattern, @NotNull String name, @NotNull GlobalSearchScope scope, @Nullable IdFilter idFilter) {
    myCompletePattern = pattern;
    myLocalPatternName = name;
    mySearchScope = scope;
    myIdFilter = idFilter;
  }

  public String getCompletePattern() {
    return myCompletePattern;
  }

  public String getLocalPatternName() {
    return myLocalPatternName;
  }

  public @NotNull GlobalSearchScope getSearchScope() {
    return mySearchScope;
  }

  public @Nullable IdFilter getIdFilter() {
    return myIdFilter;
  }

  public static FindSymbolParameters wrap(@NotNull String pattern, @NotNull Project project, boolean searchInLibraries) {
    return new FindSymbolParameters(
      pattern,
      pattern,
      searchScopeFor(project, searchInLibraries),
      null
    );
  }

  public static GlobalSearchScope searchScopeFor(@Nullable Project project, boolean searchInLibraries) {
    GlobalSearchScope baseScope =
      project == null ? new EverythingGlobalScope() :
      searchInLibraries ? ProjectScope.getAllScope(project) : ProjectScope.getProjectScope(project);

    return baseScope.intersectWith(new EverythingGlobalScope(project) {
      @Override
      public boolean contains(@NotNull VirtualFile file) {
        if (file.getFileSystem() instanceof HiddenFileSystem) return false;
        Project project = getProject();
        RootType rootType = RootType.forFile(file);
        if (rootType != null && (rootType.isHidden() || project != null && rootType.isIgnored(project, file))) return false;
        return true;
      }
    });
  }

  public Project getProject() {
    return mySearchScope.getProject();
  }

  public boolean isSearchInLibraries() {
    return mySearchScope.isSearchInLibraries();
  }
}
