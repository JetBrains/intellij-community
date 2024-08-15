// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName;

import com.intellij.ide.actions.JavaQualifiedNameProvider;
import com.intellij.ide.util.gotoByName.DefaultClassNavigationContributor.DefaultClassProcessor;
import com.intellij.lang.Language;
import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.GotoClassContributor;
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
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;

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

  public static boolean isOpenable(PsiMember member) {
    final PsiFile file = member.getContainingFile();
    return file != null && file.getVirtualFile() != null;
  }

  private static boolean hasSuperMethodCandidates(final PsiMethod method,
                                                  final GlobalSearchScope scope,
                                                  final Predicate<? super PsiMember> qualifiedMatcher) {
    if (method.hasModifierProperty(PsiModifier.PRIVATE) || method.hasModifierProperty(PsiModifier.STATIC)) return false;

    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return false;

    final int parametersCount = method.getParameterList().getParametersCount();
    return !InheritanceUtil.processSupers(containingClass, false, superClass -> {
      if (PsiSearchScopeUtil.isInScope(scope, superClass)) {
        for (PsiMethod candidate : superClass.findMethodsByName(method.getName(), false)) {
          if (parametersCount == candidate.getParameterList().getParametersCount() &&
              !candidate.hasModifierProperty(PsiModifier.PRIVATE) &&
              !candidate.hasModifierProperty(PsiModifier.STATIC) &&
              qualifiedMatcher.test(candidate)) {
            return false;
          }
        }
      }
      return true;
    });
  }

  private static boolean hasSuperMethod(Project project,
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

  private static PsiShortNamesCache getPsiShortNamesCache(@NotNull Project project) {
    Set<Language> withoutLanguages = IgnoreLanguageInDefaultProvider.getIgnoredLanguages();
    return PsiShortNamesCache.getInstance(project).withoutLanguages(withoutLanguages);
  }

  @Override
  public void processNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    Project project = scope.getProject();
    if (project == null) return;
    DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE.ignoreDumbMode(() -> {
      PsiShortNamesCache cache = getPsiShortNamesCache(project);
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
    final Predicate<PsiMember> qualifiedMatcher = getQualifiedNameMatcher(completePattern);
    final Set<PsiMethod> collectedMethods = new HashSet<>();
    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
      PsiShortNamesCache cache = getPsiShortNamesCache(project);
      boolean success = cache.processFieldsWithName(name, field -> {
        if (isOpenable(field) && qualifiedMatcher.test(field)) return processor.process(field);
        return true;
      }, scope, filter) &&
                        cache.processClassesWithName(name, new DefaultClassProcessor(processor, parameters, true), scope, filter) &&
                        cache.processMethodsWithName(name, method -> {
                          if (!method.isConstructor() && isOpenable(method) && qualifiedMatcher.test(method)) {
                            collectedMethods.add(method);
                          }
                          return true;
                        }, scope, filter);
      if (success) {
        // hashSuperMethod can access index and can not be invoked without risk of the deadlock in processMethodsWithName
        Iterator<PsiMethod> iterator = collectedMethods.iterator();
        while (iterator.hasNext()) {
          PsiMethod method = iterator.next();
          if (!hasSuperMethod(project, method, scope, qualifiedMatcher, completePattern) && !processor.process(method)) return;
          ProgressManager.checkCanceled();
          iterator.remove();
        }
      }
    });
  }

  private static Predicate<PsiMember> getQualifiedNameMatcher(String completePattern) {
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