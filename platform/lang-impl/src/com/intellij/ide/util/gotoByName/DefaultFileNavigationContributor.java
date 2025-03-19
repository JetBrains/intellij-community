// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName;

import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class DefaultFileNavigationContributor implements ChooseByNameContributorEx, DumbAware {
  private static final Logger LOG = Logger.getInstance(DefaultFileNavigationContributor.class);

  @Override
  public void processNames(final @NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, IdFilter filter) {
    long start = System.nanoTime();
    FilenameIndex.processAllFileNames(processor, scope, filter);
    if (LOG.isDebugEnabled()) {
      LOG.debug("All names retrieved:" + TimeoutUtil.getDurationMillis(start));
    }
  }

  @Override
  public void processElementsWithName(@NotNull String name,
                                      @NotNull Processor<? super NavigationItem> processor,
                                      @NotNull FindSymbolParameters parameters) {
    boolean globalSearch = parameters.getSearchScope().isSearchInLibraries();
    boolean directoriesOnly = isDirectoryOnlyPattern(parameters);
    boolean withFiles = !directoriesOnly;
    boolean withDirs = directoriesOnly || Registry.is("ide.goto.file.include.directories");
    PsiManager psiManager = PsiManager.getInstance(parameters.getProject());
    FilenameIndex.processFilesByNames(
      Set.of(name), true, parameters.getSearchScope(), parameters.getIdFilter(), file -> {
        if (!file.isValid()) return true;
        boolean isDir = file.isDirectory();
        if (!withFiles && !isDir || !withDirs && isDir) return true;

        if (!globalSearch && ProjectUtil.isProjectOrWorkspaceFile(file)) {
          return true;
        }
        PsiFileSystemItem psi = isDir ? psiManager.findDirectory(file) : psiManager.findFile(file);
        if (psi == null) return true;
        return processor.process(psi);
      });
  }

  private static boolean isDirectoryOnlyPattern(@NotNull FindSymbolParameters parameters) {
    String completePattern = parameters.getCompletePattern();
    return completePattern.endsWith("/") || completePattern.endsWith("\\");
  }
}
