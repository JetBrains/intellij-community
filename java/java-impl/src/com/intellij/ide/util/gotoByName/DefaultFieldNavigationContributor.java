// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName;

import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMember;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.Processor;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

import static com.intellij.ide.util.gotoByName.DefaultClassNavigationContributor.getPsiShortNamesCache;

final class DefaultFieldNavigationContributor implements ChooseByNameContributorEx, PossiblyDumbAware {
  @Override
  public void processNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    Project project = scope.getProject();
    if (project == null) return;
    PsiShortNamesCache cache = getPsiShortNamesCache(project);
    DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE.ignoreDumbMode(() -> {
      cache.processAllFieldNames(processor, scope, filter);
    });
  }

  @Override
  public void processElementsWithName(@NotNull String name,
                                      final @NotNull Processor<? super NavigationItem> processor,
                                      final @NotNull FindSymbolParameters parameters) {
    GlobalSearchScope scope = parameters.getSearchScope();
    Project project = scope.getProject();
    if (project == null) return;
    IdFilter filter = parameters.getIdFilter();
    String completePattern = parameters.getCompletePattern();
    final Predicate<PsiMember> qualifiedMatcher = DefaultMethodNavigationContributor.getQualifiedNameMatcher(completePattern);
    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
      PsiShortNamesCache cache = getPsiShortNamesCache(project);
      cache.processFieldsWithName(name, field -> {
        if (DefaultClassNavigationContributor.isOpenable(field) && qualifiedMatcher.test(field)) return processor.process(field);
        return true;
      }, scope, filter);
    });
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}