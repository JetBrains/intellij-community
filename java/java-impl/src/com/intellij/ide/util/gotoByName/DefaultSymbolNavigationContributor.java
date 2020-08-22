// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName;

import com.intellij.ide.actions.JavaQualifiedNameProvider;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.GotoClassContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.diagnostic.Logger;
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
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;

public class DefaultSymbolNavigationContributor implements ChooseByNameContributorEx, GotoClassContributor {
  private static final Logger LOG = Logger.getInstance(DefaultSymbolNavigationContributor.class);

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
                                                  final Predicate<PsiMember> qualifiedMatcher) {
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

  private static boolean hasSuperMethod(PsiMethod method, GlobalSearchScope scope, Predicate<PsiMember> qualifiedMatcher, String pattern) {
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
    final Set<PsiMethod> collectedMethods = new THashSet<>();
    boolean success = cache.processFieldsWithName(name, field -> {
      if (isOpenable(field) && qualifiedMatcher.test(field)) return processor.process(field);
      return true;
    }, scope, filter) &&
                      cache.processClassesWithName(name, aClass -> {
                        if (isOpenable(aClass) && qualifiedMatcher.test(aClass)) return processor.process(aClass);
                        return true;
                      }, scope, filter) &&
                      cache.processMethodsWithName(name, method -> {
                      if(!method.isConstructor() && isOpenable(method) && qualifiedMatcher.test(method)) {
                        collectedMethods.add(method);
                      }
                      return true;
                    }, scope, filter);
    if (success) {
      // hashSuperMethod accesses index and can not be invoked without risk of the deadlock in processMethodsWithName
      Iterator<PsiMethod> iterator = collectedMethods.iterator();
      while(iterator.hasNext()) {
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
    return __->true;
  }

  private static class MyComparator implements Comparator<PsiModifierListOwner>{
    public static final MyComparator INSTANCE = new MyComparator();

    private final DefaultPsiElementCellRenderer myRenderer = new DefaultPsiElementCellRenderer();

    @Override
    public int compare(PsiModifierListOwner element1, PsiModifierListOwner element2) {
      if (element1 == element2) return 0;

      PsiModifierList modifierList1 = element1.getModifierList();
      PsiModifierList modifierList2 = element2.getModifierList();

      int level1 = modifierList1 == null ? PsiUtil.ACCESS_LEVEL_PUBLIC : PsiUtil.getAccessLevel(modifierList1);
      int level2 = modifierList2 == null ? PsiUtil.ACCESS_LEVEL_PUBLIC : PsiUtil.getAccessLevel(modifierList2);
      if (level1 != level2) return level2 - level1;

      int kind1 = getElementTypeLevel(element1);
      int kind2 = getElementTypeLevel(element2);
      if (kind1 != kind2) return kind1 - kind2;

      if (element1 instanceof PsiMethod){
        LOG.assertTrue(element2 instanceof PsiMethod);
        PsiParameter[] params1 = ((PsiMethod)element1).getParameterList().getParameters();
        PsiParameter[] params2 = ((PsiMethod)element2).getParameterList().getParameters();

        if (params1.length != params2.length) return params1.length - params2.length;
      }

      String text1 = myRenderer.getElementText(element1);
      String text2 = myRenderer.getElementText(element2);
      if (!text1.equals(text2)) return text1.compareTo(text2);

      String containerText1 = myRenderer.getContainerText(element1, text1);
      String containerText2 = myRenderer.getContainerText(element2, text2);
      if (containerText1 == null) containerText1 = "";
      if (containerText2 == null) containerText2 = "";
      return containerText1.compareTo(containerText2);
    }

    private static int getElementTypeLevel(PsiElement element){
      if (element instanceof PsiMethod){
        return 1;
      }
      else if (element instanceof PsiField){
        return 2;
      }
      else if (element instanceof PsiClass){
        return 3;
      }
      else{
        LOG.error(element);
        return 0;
      }
    }
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
