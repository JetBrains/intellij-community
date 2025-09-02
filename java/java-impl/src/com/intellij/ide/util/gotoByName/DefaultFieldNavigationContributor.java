// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName;

import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.Processor;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

import static com.intellij.ide.util.gotoByName.DefaultClassNavigationContributor.getPsiShortNamesCache;

@ApiStatus.Internal
public final class DefaultFieldNavigationContributor implements ChooseByNameContributorEx, PossiblyDumbAware {
  @Override
  public void processNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    Project project = scope.getProject();
    if (project == null) return;
    DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE.ignoreDumbMode(() -> {
      getPsiShortNamesCache(project).processAllFieldNames(processor, scope, filter);
      StubIndex.getInstance().processAllKeys(JavaStubIndexKeys.RECORD_COMPONENTS, processor, scope, filter);
    });
  }

  @Override
  public void processElementsWithName(@NotNull String name,
                                      @NotNull Processor<? super NavigationItem> processor,
                                      @NotNull FindSymbolParameters parameters) {
    GlobalSearchScope scope = parameters.getSearchScope();
    Project project = scope.getProject();
    if (project == null) return;
    IdFilter filter = parameters.getIdFilter();
    Predicate<PsiMember> qualifiedMatcher = DefaultMethodNavigationContributor.getQualifiedNameMatcher(parameters.getCompletePattern());
    Processor<PsiMember> filteringProcessor =
      member -> !DefaultClassNavigationContributor.isOpenable(member) || !qualifiedMatcher.test(member) || processor.process(member);
    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
      getPsiShortNamesCache(project).processFieldsWithName(name, filteringProcessor, scope, filter);
      StubIndex.getInstance()
        .processElements(JavaStubIndexKeys.RECORD_COMPONENTS, name, project, scope, PsiRecordComponent.class, filteringProcessor);
    });
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}