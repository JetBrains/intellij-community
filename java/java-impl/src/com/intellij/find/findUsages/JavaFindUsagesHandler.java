/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.find.findUsages;

import com.intellij.CommonBundle;
import com.intellij.find.FindBundle;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.search.ThrowSearchUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.util.JavaNonCodeSearchElementDescriptionProvider;
import com.intellij.refactoring.util.NonCodeSearchDescriptionLocation;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public class JavaFindUsagesHandler extends FindUsagesHandler{
  private static final Logger LOG = Logger.getInstance("#com.intellij.find.findUsages.JavaFindUsagesHandler");
  protected static final String ACTION_STRING = FindBundle.message("find.super.method.warning.action.verb");

  private final PsiElement[] myElementsToSearch;
  private final JavaFindUsagesHandlerFactory myFactory;

  public JavaFindUsagesHandler(@NotNull PsiElement psiElement, @NotNull JavaFindUsagesHandlerFactory factory) {
    this(psiElement, PsiElement.EMPTY_ARRAY, factory);
  }

  public JavaFindUsagesHandler(@NotNull PsiElement psiElement, @NotNull PsiElement[] elementsToSearch, @NotNull JavaFindUsagesHandlerFactory factory) {
    super(psiElement);
    myElementsToSearch = elementsToSearch;
    myFactory = factory;
  }

  @Override
  @NotNull
  public AbstractFindUsagesDialog getFindUsagesDialog(boolean isSingleFile, boolean toShowInNewTab, boolean mustOpenInNewTab) {
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

  private static boolean askWhetherShouldSearchForParameterInOverridingMethods(@NotNull PsiElement psiElement, @NotNull PsiParameter parameter) {
    return Messages.showOkCancelDialog(psiElement.getProject(),
                               FindBundle.message("find.parameter.usages.in.overriding.methods.prompt", parameter.getName()),
                               FindBundle.message("find.parameter.usages.in.overriding.methods.title"),
                               CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText(),
                               Messages.getQuestionIcon()) == Messages.OK;
  }

  @NotNull
  private static PsiElement[] getParameterElementsToSearch(@NotNull PsiParameter parameter, @NotNull PsiMethod method) {
    PsiMethod[] overrides = OverridingMethodsSearch.search(method).toArray(PsiMethod.EMPTY_ARRAY);
    for (int i = 0; i < overrides.length; i++) {
      final PsiElement navigationElement = overrides[i].getNavigationElement();
      if (navigationElement instanceof PsiMethod) {
        overrides[i] = (PsiMethod)navigationElement;
      }
    }
    List<PsiElement> elementsToSearch = new ArrayList<>(overrides.length + 1);
    elementsToSearch.add(parameter);
    int idx = method.getParameterList().getParameterIndex(parameter);
    for (PsiMethod override : overrides) {
      final PsiParameter[] parameters = override.getParameterList().getParameters();
      if (idx < parameters.length) {
        elementsToSearch.add(parameters[idx]);
      }
    }
    return PsiUtilCore.toPsiElementArray(elementsToSearch);
  }


  @Override
  @NotNull
  public PsiElement[] getPrimaryElements() {
    final PsiElement element = getPsiElement();
    if (element instanceof PsiParameter) {
      final PsiParameter parameter = (PsiParameter)element;
      final PsiElement scope = parameter.getDeclarationScope();
      if (scope instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)scope;
        if (PsiUtil.canBeOverriden(method)) {
          final PsiClass aClass = method.getContainingClass();
          LOG.assertTrue(aClass != null); //Otherwise can not be overriden

          boolean hasOverridden = OverridingMethodsSearch.search(method).findFirst() != null;
          if (hasOverridden && askWhetherShouldSearchForParameterInOverridingMethods(element, parameter)) {
            return getParameterElementsToSearch(parameter, method);
          }
        }
      }
    }
    return myElementsToSearch.length == 0 ? new PsiElement[]{element} : myElementsToSearch;
  }

  @Override
  @NotNull
  public PsiElement[] getSecondaryElements() {
    PsiElement element = getPsiElement();
    if (ApplicationManager.getApplication().isUnitTestMode()) return PsiElement.EMPTY_ARRAY;
    if (element instanceof PsiField) {
      final PsiField field = (PsiField)element;
      PsiClass containingClass = field.getContainingClass();
      if (containingClass != null) {
        String fieldName = field.getName();
        final String propertyName = JavaCodeStyleManager.getInstance(getProject()).variableNameToPropertyName(fieldName, VariableKind.FIELD);
        Set<PsiMethod> accessors = new THashSet<>();
        boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
        PsiMethod getter = PropertyUtil.findPropertyGetterWithType(propertyName, isStatic, field.getType(),
                                     ContainerUtil.iterate(containingClass.getMethods()));
        if (getter != null) accessors.add(getter);
        PsiMethod setter = PropertyUtil.findPropertySetterWithType(propertyName, isStatic, field.getType(),
                                     ContainerUtil.iterate(containingClass.getMethods()));
        if (setter != null) accessors.add(setter);
        accessors.addAll(PropertyUtil.getAccessors(containingClass, fieldName));
        if (!accessors.isEmpty()) {
          boolean containsPhysical = ContainerUtil.find(accessors, psiMethod -> psiMethod.isPhysical()) != null;
          final boolean doSearch = !containsPhysical ||
                                   Messages.showOkCancelDialog(FindBundle.message("find.field.accessors.prompt", fieldName),
                                                               FindBundle.message("find.field.accessors.title"),
                                                               CommonBundle.getYesButtonText(),
                                                               CommonBundle.getNoButtonText(), Messages.getQuestionIcon()) ==
                                   Messages.OK;
          if (doSearch) {
            final Set<PsiElement> elements = new THashSet<>();
            for (PsiMethod accessor : accessors) {
              ContainerUtil.addAll(elements, SuperMethodWarningUtil.checkSuperMethods(accessor, ACTION_STRING));
            }
            return PsiUtilCore.toPsiElementArray(elements);
          }
        }
      }
    }
    return super.getSecondaryElements();
  }

  @Override
  @NotNull
  public FindUsagesOptions getFindUsagesOptions(@Nullable final DataContext dataContext) {
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
  protected Set<String> getStringsToSearch(@NotNull final PsiElement element) {
    return JavaFindUsagesHelper.getElementNames(element);
  }

  @Override
  public boolean processElementUsages(@NotNull final PsiElement element,
                                      @NotNull final Processor<UsageInfo> processor,
                                      @NotNull final FindUsagesOptions options) {
    return JavaFindUsagesHelper.processElementUsages(element, options, processor);
  }

  @Override
  protected boolean isSearchForTextOccurrencesAvailable(@NotNull PsiElement psiElement, boolean isSingleFile) {
    return !isSingleFile &&
           new JavaNonCodeSearchElementDescriptionProvider().getElementDescription(psiElement, NonCodeSearchDescriptionLocation.NON_JAVA) != null;
  }

  @NotNull
  @Override
  public Collection<PsiReference> findReferencesToHighlight(@NotNull final PsiElement target, @NotNull final SearchScope searchScope) {
    if (target instanceof PsiMethod) {
      final PsiMethod[] superMethods = ((PsiMethod)target).findDeepestSuperMethods();
      if (superMethods.length == 0) {
        return MethodReferencesSearch.search((PsiMethod)target, searchScope, true).findAll();
      }
      final Collection<PsiReference> result = new ArrayList<>();
      for (PsiMethod superMethod : superMethods) {
        result.addAll(MethodReferencesSearch.search(superMethod, searchScope, true).findAll());
      }
      return result;
    }
    return super.findReferencesToHighlight(target, searchScope);
  }
}
