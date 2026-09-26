// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages;

import com.intellij.find.FindBundle;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.HierarchicalMethodSignature;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor;
import com.intellij.psi.impl.search.ThrowSearchUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.util.JavaNonCodeSearchElementDescriptionProvider;
import com.intellij.refactoring.util.NonCodeSearchDescriptionLocation;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class JavaFindUsagesHandler extends FindUsagesHandler {
  private static final Logger LOG = Logger.getInstance(JavaFindUsagesHandler.class);

  private final PsiElement[] myElementsToSearch;
  private final JavaFindUsagesHandlerFactory myFactory;

  public JavaFindUsagesHandler(@NotNull PsiElement psiElement, @NotNull JavaFindUsagesHandlerFactory factory) {
    this(psiElement, PsiElement.EMPTY_ARRAY, factory);
  }

  public JavaFindUsagesHandler(@NotNull PsiElement psiElement, PsiElement @NotNull [] elementsToSearch, @NotNull JavaFindUsagesHandlerFactory factory) {
    super(psiElement);
    myElementsToSearch = elementsToSearch;
    myFactory = factory;
  }

  @Override
  public @NotNull AbstractFindUsagesDialog getFindUsagesDialog(boolean isSingleFile, boolean toShowInNewTab, boolean mustOpenInNewTab) {
    PsiElement element = getPsiElement();
    if (element instanceof PsiPackage) {
      return new FindPackageUsagesDialog(element, getProject(), myFactory.getFindPackageOptions(), toShowInNewTab, mustOpenInNewTab, isSingleFile, this);
    }
    if (element instanceof PsiClass) {
      return new FindClassUsagesDialog(element, getProject(), myFactory.getFindClassOptions(), toShowInNewTab, mustOpenInNewTab, isSingleFile, this);
    }
    if (element instanceof PsiMethod) {
      return new FindMethodUsagesDialog(element, getProject(), myFactory.getFindMethodOptions(), toShowInNewTab, mustOpenInNewTab, isSingleFile, this);
    }
    if (element instanceof PsiVariable) {
      return new FindVariableUsagesDialog(element, getProject(), myFactory.getFindVariableOptions(), toShowInNewTab, mustOpenInNewTab, isSingleFile, this);
    }
    if (ThrowSearchUtil.isSearchable(element)) {
      return new FindThrowUsagesDialog(element, getProject(), myFactory.getFindThrowOptions(), toShowInNewTab, mustOpenInNewTab, isSingleFile, this);
    }
    return super.getFindUsagesDialog(isSingleFile, toShowInNewTab, mustOpenInNewTab);
  }

  private static PsiElement @NotNull [] getParameterElementsToSearch(@NotNull PsiParameter parameter, @NotNull PsiMethod method) {
    PsiMethod[] overrides = OverridingMethodsSearch.search(method).toArray(PsiMethod.EMPTY_ARRAY);
    for (int i = 0; i < overrides.length; i++) {
      final PsiElement navigationElement = ReadAction.compute(overrides[i]::getNavigationElement);
      if (navigationElement instanceof PsiMethod m) {
        overrides[i] = m;
      }
    }
    List<PsiElement> elementsToSearch = new ArrayList<>(overrides.length + 1);
    elementsToSearch.add(parameter);
    int idx = ReadAction.compute(() -> method.getParameterList().getParameterIndex(parameter));
    for (PsiMethod override : overrides) {
      final PsiParameter[] parameters = ReadAction.compute(() -> override.getParameterList().getParameters());
      if (idx < parameters.length) {
        elementsToSearch.add(parameters[idx]);
      }
    }

    FunctionalExpressionSearch.search(method).asIterable().forEach(element -> {
      if (element instanceof PsiLambdaExpression lambda) {
        PsiParameter[] parameters = ReadAction.compute(() -> lambda.getParameterList().getParameters());
        if (idx < parameters.length) {
          elementsToSearch.add(parameters[idx]);
        }
      }
    });

    return PsiUtilCore.toPsiElementArray(elementsToSearch);
  }


  @Override
  public PsiElement @NotNull [] getPrimaryElements() {
    final PsiElement element = getPsiElement();
    if (element instanceof PsiParameter parameter) {
      if (parameter.getDeclarationScope() instanceof PsiMethod method && PsiUtil.canBeOverridden(method)) {
        final PsiClass aClass = method.getContainingClass();
        LOG.assertTrue(aClass != null); //Otherwise can not be overridden

        ProgressManager pm = ProgressManager.getInstance();
        boolean hasOverriden = pm.runProcessWithProgressSynchronously(() ->
                                                                        OverridingMethodsSearch.search(method).findFirst() != null ||
                                                                        FunctionalExpressionSearch.search(method).findFirst() != null,
                                                                      JavaBundle.message("progress.title.detect.overridden.methods"), true,
                                                                      getProject()) == Boolean.TRUE;

        if (hasOverriden && myFactory.getFindVariableOptions().isSearchInOverridingMethods) {
          return pm.runProcessWithProgressSynchronously(() -> getParameterElementsToSearch(parameter, method),
                                                        JavaBundle.message("progress.title.detect.overridden.methods"), true, getProject());
        }
      }
    }
    else if (element instanceof PsiMethod method && myFactory.getFindMethodOptions().isSearchForBaseMethod &&
             //temporary workaround
             !DumbService.isDumb(element.getProject())) {
      return SuperMethodWarningUtil.getTargetMethodCandidates(method, Collections.emptyList());
    }
    return myElementsToSearch.length == 0 ? new PsiElement[]{element} : myElementsToSearch;
  }

  @Override
  public PsiElement @NotNull [] getSecondaryElements() {
    PsiElement element = getPsiElement();
    if (element instanceof PsiField field) {
      Set<PsiMethod> accessors = getFieldAccessors(field);
      if (!accessors.isEmpty()) {
        boolean containsPhysical = ContainerUtil.find(accessors, psiMethod -> psiMethod.isPhysical()) != null;
        boolean doSearch = !containsPhysical || myFactory.getFindVariableOptions().isSearchForAccessors;
        if (doSearch) {
          Set<PsiElement> elements = new HashSet<>();
          for (PsiMethod accessor : accessors) {
            if (myFactory.getFindVariableOptions().isSearchForBaseAccessors) {
              ContainerUtil.addAll(elements, SuperMethodWarningUtil.getTargetMethodCandidates(accessor, Collections.emptyList()));
            }
            else {
              elements.add(accessor);
            }
          }
          return PsiUtilCore.toPsiElementArray(elements);
        }
      }
    }
    else if (element instanceof PsiClass aClass && aClass.isRecord()) {
      return ContainerUtil.findAllAsArray(aClass.getConstructors(), LightRecordCanonicalConstructor.class);
    }
    return super.getSecondaryElements();
  }

  Set<PsiMethod> getFieldAccessors(PsiField field) {
    Set<PsiMethod> accessors = new HashSet<>();
    PsiClass containingClass = field.getContainingClass();
    if (containingClass != null) {
      String fieldName = field.getName();
      final String propertyName = JavaCodeStyleManager.getInstance(getProject()).variableNameToPropertyName(fieldName, VariableKind.FIELD);
      boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
      Collection<PsiMethod> methods = Arrays.asList(containingClass.getMethods());
      PsiMethod getter = PropertyUtilBase.findPropertyGetterWithType(propertyName, isStatic, field.getType(), methods);
      if (getter != null) accessors.add(getter);
      PsiMethod setter = PropertyUtilBase.findPropertySetterWithType(propertyName, isStatic, field.getType(), methods);
      if (setter != null) accessors.add(setter);
      accessors.addAll(PropertyUtilBase.getAccessors(containingClass, fieldName));
      accessors.removeIf(accessor -> field != PropertyUtilBase.findPropertyFieldByMember(accessor));
    }
    return accessors;
  }

  @Override
  public @NotNull FindUsagesOptions getFindUsagesOptions(@Nullable DataContext dataContext) {
    PsiElement element = getPsiElement();
    if (element instanceof PsiPackage) {
      return myFactory.getFindPackageOptions();
    }
    if (element instanceof PsiClass) {
      return myFactory.getFindClassOptions();
    }
    if (element instanceof PsiMethod) {
      return myFactory.getFindMethodOptions();
    }
    if (element instanceof PsiVariable) {
      return myFactory.getFindVariableOptions();
    }
    if (ThrowSearchUtil.isSearchable(element)) {
      return myFactory.getFindThrowOptions();
    }
    return super.getFindUsagesOptions(dataContext);
  }

  @Override
  protected Set<String> getStringsToSearch(@NotNull PsiElement element) {
    return JavaFindUsagesHelper.getElementNames(element);
  }

  @Override
  public boolean processElementUsages(@NotNull PsiElement element,
                                      @NotNull Processor<? super UsageInfo> processor,
                                      @NotNull FindUsagesOptions options) {
    return JavaFindUsagesHelper.processElementUsages(element, options, processor);
  }

  @Override
  protected boolean isSearchForTextOccurrencesAvailable(@NotNull PsiElement psiElement, boolean isSingleFile) {
    return !isSingleFile &&
           new JavaNonCodeSearchElementDescriptionProvider().getElementDescription(psiElement, NonCodeSearchDescriptionLocation.NON_JAVA) != null;
  }

  @Override
  public @Unmodifiable @NotNull Collection<PsiReference> findReferencesToHighlight(@NotNull PsiElement target,
                                                                                   @NotNull SearchScope searchScope) {
    if (target instanceof PsiMethod method) {
      Set<PsiMethod> superTargets = new LinkedHashSet<>();
      PsiMethod[] superMethods = method.findDeepestSuperMethods();
      if (superMethods.length == 0) {
        superTargets.add(method);
      }
      if (searchScope instanceof LocalSearchScope scope) {
        GlobalSearchScope resolveScope =
          GlobalSearchScope.union(ContainerUtil.map2Array(scope.getScope(), GlobalSearchScope.class, PsiElement::getResolveScope));
        for (HierarchicalMethodSignature superSignature :
          PsiSuperMethodImplUtil.getHierarchicalMethodSignature(method, resolveScope).getSuperSignatures()) {
          PsiMethod superMethod = superSignature.getMethod();
          PsiMethod[] deepestSupers = superMethod.findDeepestSuperMethods();
          Collections.addAll(superTargets, deepestSupers.length == 0 ? new PsiMethod[]{superMethod} : deepestSupers);
        }
      }
      else {
        Collections.addAll(superTargets, superMethods);
      }

      Collection<PsiReference> result = new LinkedHashSet<>();
      for (PsiMethod superMethod : superTargets) {
        result.addAll(MethodReferencesSearch.search(superMethod, searchScope, true).findAll());
      }
      return result;
    }
    return super.findReferencesToHighlight(target, searchScope);
  }

  protected static @NotNull String getActionString() {
    return FindBundle.message("find.super.method.warning.action.verb");
  }
}
