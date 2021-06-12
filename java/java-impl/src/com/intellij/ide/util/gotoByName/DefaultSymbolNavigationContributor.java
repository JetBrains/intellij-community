// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName;

import com.intellij.ide.actions.JavaQualifiedNameProvider;
import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.GotoClassContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;

public class DefaultSymbolNavigationContributor implements ChooseByNameContributorEx, GotoClassContributor {
  @Nullable
  @Override
  public String getQualifiedName(NavigationItem item) {
    if (item instanceof PsiClass) {
      return DefaultClassNavigationContributor.getQualifiedNameForClass((PsiClass)item);
    }
    return null;
  }

  @Nullable
  @Override
  public String getQualifiedNameSeparator() {
    return "$";
  }

  private static boolean isOpenable(PsiMember member) {
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

  private static boolean hasSuperMethod(PsiMethod method,
                                        GlobalSearchScope scope,
                                        Predicate<? super PsiMember> qualifiedMatcher,
                                        String pattern) {
    if (pattern.contains(".") && Registry.is("ide.goto.symbol.include.overrides.on.qualified.patterns")) {
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

  @Override
  public void processNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(scope.getProject());
    cache.processAllClassNames(processor, scope, filter);
    cache.processAllFieldNames(processor, scope, filter);
    cache.processAllMethodNames(processor, scope, filter);
  }

  @Override
  public void processElementsWithName(@NotNull String name,
                                      @NotNull final Processor<? super NavigationItem> processor,
                                      @NotNull final FindSymbolParameters parameters) {

    GlobalSearchScope scope = parameters.getSearchScope();
    IdFilter filter = parameters.getIdFilter();
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(scope.getProject());

    String completePattern = parameters.getCompletePattern();
    final Predicate<PsiMember> qualifiedMatcher = getQualifiedNameMatcher(completePattern);

    //noinspection UnusedDeclaration
    final Set<PsiMethod> collectedMethods = new HashSet<>();
    boolean success = cache.processFieldsWithName(name, field -> {
      if (isOpenable(field) && qualifiedMatcher.test(field)) return processor.process(field);
      return true;
    }, scope, filter) &&
                      cache.processClassesWithName(name, aClass -> {
                        if (isOpenable(aClass) && qualifiedMatcher.test(aClass)) return processor.process(aClass);
                        return true;
                      }, scope, filter) &&
                      cache.processMethodsWithName(name, method -> {
                        if (!method.isConstructor() && isOpenable(method) && qualifiedMatcher.test(method)) {
                          collectedMethods.add(method);
                        }
                        return true;
                      }, scope, filter);
    if (success) {
      // hashSuperMethod accesses index and can not be invoked without risk of the deadlock in processMethodsWithName
      Iterator<PsiMethod> iterator = collectedMethods.iterator();
      while (iterator.hasNext()) {
        PsiMethod method = iterator.next();
        if (!hasSuperMethod(method, scope, qualifiedMatcher, completePattern) && !processor.process(method)) return;
        ProgressManager.checkCanceled();
        iterator.remove();
      }
    }
  }

  private static Predicate<PsiMember> getQualifiedNameMatcher(String completePattern) {
    if (completePattern.contains("#") && completePattern.endsWith(")")) {
      return member -> member instanceof PsiMethod && JavaQualifiedNameProvider.hasQualifiedName(completePattern, (PsiMethod)member);
    }

    if (completePattern.contains(".") || completePattern.contains("#")) {
      String normalized = StringUtil.replace(StringUtil.replace(completePattern, "#", ".*"), ".", ".*");
      MinusculeMatcher matcher = NameUtil.buildMatcher("*" + normalized).build();
      return member -> {
        String qualifiedName = PsiUtil.getMemberQualifiedName(member);
        return qualifiedName != null && matcher.matches(qualifiedName);
      };
    }
    return __ -> true;
  }

  public static class JavadocSeparatorContributor implements ChooseByNameContributorEx, GotoClassContributor {
    @Nullable
    @Override
    public String getQualifiedName(NavigationItem item) {
      return null;
    }

    @Nullable
    @Override
    public String getQualifiedNameSeparator() {
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
