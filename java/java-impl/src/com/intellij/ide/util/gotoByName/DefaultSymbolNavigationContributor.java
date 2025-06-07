// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName;

import com.intellij.ide.util.gotoByName.DefaultClassNavigationContributor.DefaultClassProcessor;
import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.GotoClassContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.Processor;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;

@ApiStatus.Obsolete
@SuppressWarnings("ALL")
public class DefaultSymbolNavigationContributor implements ChooseByNameContributorEx, GotoClassContributor, PossiblyDumbAware {
  @Override
  public @Nullable String getQualifiedName(@NotNull NavigationItem item) {
    if (item instanceof PsiClass) {
      return DefaultClassNavigationContributor.getQualifiedNameForClass((PsiClass)item);
    }
    return null;
  }

  @Override
  public @Nullable String getQualifiedNameSeparator() {
    return "$";
  }

  @Override
  public void processNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    Project project = scope.getProject();
    if (project == null) return;
    DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE.ignoreDumbMode(() -> {
      PsiShortNamesCache cache = DefaultClassNavigationContributor.getPsiShortNamesCache(project);
      cache.processAllClassNames(processor, scope, filter);
      cache.processAllFieldNames(processor, scope, filter);
      cache.processAllMethodNames(processor, scope, filter);
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
    final Set<PsiMethod> collectedMethods = new HashSet<>();
    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
      PsiShortNamesCache cache = DefaultClassNavigationContributor.getPsiShortNamesCache(project);
      boolean success = cache.processFieldsWithName(name, field -> {
        if (DefaultClassNavigationContributor.isOpenable(field) && qualifiedMatcher.test(field)) return processor.process(field);
        return true;
      }, scope, filter) &&
                        cache.processClassesWithName(name, new DefaultClassProcessor(processor, parameters, true), scope, filter) &&
                        cache.processMethodsWithName(name, method -> {
                          if (!method.isConstructor() &&
                              DefaultClassNavigationContributor.isOpenable(method) &&
                              qualifiedMatcher.test(method)) {
                            collectedMethods.add(method);
                          }
                          return true;
                        }, scope, filter);
      if (success) {
        // hashSuperMethod can access index and can not be invoked without risk of the deadlock in processMethodsWithName
        Iterator<PsiMethod> iterator = collectedMethods.iterator();
        while (iterator.hasNext()) {
          PsiMethod method = iterator.next();
          if (!DefaultMethodNavigationContributor.hasSuperMethod(project, method, scope, qualifiedMatcher, completePattern) && !processor.process(method)) return;
          ProgressManager.checkCanceled();
          iterator.remove();
        }
      }
    });
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }

  public static final class JavadocSeparatorContributor implements ChooseByNameContributorEx, GotoClassContributor {
    @Override
    public @Nullable String getQualifiedName(@NotNull NavigationItem item) {
      return null;
    }

    @Override
    public @Nullable String getQualifiedNameSeparator() {
      return "#";
    }

    @Override
    public void processNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    }

    @Override
    public void processElementsWithName(@NotNull String name,
                                        @NotNull Processor<? super NavigationItem> processor,
                                        @NotNull FindSymbolParameters parameters) {
    }
  }
}