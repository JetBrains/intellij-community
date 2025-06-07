// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName;

import com.intellij.ide.actions.JavaQualifiedNameProvider;
import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Predicates;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

@ApiStatus.Internal
public final class DefaultMethodNavigationContributor implements ChooseByNameContributorEx, PossiblyDumbAware {
  @Override
  public void processNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    Project project = scope.getProject();
    if (project == null) return;
    PsiShortNamesCache cache = DefaultClassNavigationContributor.getPsiShortNamesCache(project);
    DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE.ignoreDumbMode(() -> {
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
    final Predicate<PsiMember> qualifiedMatcher = getQualifiedNameMatcher(completePattern);
    final Set<PsiMethod> collectedMethods = new HashSet<>();
    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
      PsiShortNamesCache cache = DefaultClassNavigationContributor.getPsiShortNamesCache(project);
      boolean success = cache.processMethodsWithName(name, method -> {
        if (!method.isConstructor() && DefaultClassNavigationContributor.isOpenable(method) && qualifiedMatcher.test(method)) {
          collectedMethods.add(method);
        }
        return true;
      }, scope, filter);
      if (success) {
        for (PsiMethod method : collectedMethods) {
          ProgressManager.checkCanceled();
          if (!hasSuperMethod(project, method, scope, qualifiedMatcher, completePattern) && !processor.process(method)) {
            return;
          }
        }
      }
    });
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }

  static boolean hasSuperMethod(Project project,
                                        PsiMethod method,
                                        GlobalSearchScope scope,
                                        Predicate<? super PsiMember> qualifiedMatcher,
                                        String pattern) {
    if (pattern.contains(".") && Registry.is("ide.goto.symbol.include.overrides.on.qualified.patterns")) {
      return false;
    }

    if (DumbService.getInstance(project).isDumb()) {
      return false;
    }

    if (!hasSuperMethodCandidates(method, scope, qualifiedMatcher)) {
      return false;
    }

    for (HierarchicalMethodSignature signature : method.getHierarchicalMethodSignature().getSuperSignatures()) {
      PsiMethod superMethod = signature.getMethod();
      if (PsiSearchScopeUtil.isInScope(scope, superMethod) && qualifiedMatcher.test(superMethod)) {
        return true;
      }
    }
    return false;
  }

  static boolean hasSuperMethodCandidates(final PsiMethod method,
                                                  final GlobalSearchScope scope,
                                                  final Predicate<? super PsiMember> qualifiedMatcher) {
    if (method.hasModifierProperty(PsiModifier.PRIVATE) || method.hasModifierProperty(PsiModifier.STATIC)) return false;

    final PsiClass containingClass = method.getContainingClass();
    return containingClass != null;
  }

  static Predicate<PsiMember> getQualifiedNameMatcher(String completePattern) {
    if (completePattern.contains("#") && completePattern.endsWith(")")) {
      return member -> member instanceof PsiMethod && JavaQualifiedNameProvider.hasQualifiedName(completePattern, (PsiMethod)member);
    }

    if (completePattern.contains(".") || completePattern.contains("#") || completePattern.contains("$")) {
      String normalized = completePattern.replace("#", ".*").replace(".", ".*").replace("$", ".*");
      MinusculeMatcher matcher = NameUtil.buildMatcher("*" + normalized).build();
      return member -> {
        String qualifiedName = PsiUtil.getMemberQualifiedName(member);
        return qualifiedName != null && matcher.matches(qualifiedName);
      };
    }
    return Predicates.alwaysTrue();
  }
}