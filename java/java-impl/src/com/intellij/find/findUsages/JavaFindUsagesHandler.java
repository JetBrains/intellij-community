// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.findUsages;

import com.intellij.CommonBundle;
import com.intellij.find.FindBundle;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public class JavaFindUsagesHandler extends FindUsagesHandler{
  private static final Logger LOG = Logger.getInstance(JavaFindUsagesHandler.class);
  /**
   * @deprecated Use {@link #getActionString()} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  protected static  final String ACTION_STRING = "to find usages of";

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
                               JavaBundle.message("find.parameter.usages.in.overriding.methods.prompt", parameter.getName()),
                               JavaBundle.message("find.parameter.usages.in.overriding.methods.title"),
                               CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText(),
                               Messages.getQuestionIcon()) == Messages.OK;
  }

  private static PsiElement @NotNull [] getParameterElementsToSearch(@NotNull PsiParameter parameter, @NotNull PsiMethod method) {
    PsiMethod[] overrides = OverridingMethodsSearch.search(method).toArray(PsiMethod.EMPTY_ARRAY);
    for (int i = 0; i < overrides.length; i++) {
      final PsiElement navigationElement = overrides[i].getNavigationElement();
      if (navigationElement instanceof PsiMethod) {
        overrides[i] = (PsiMethod)navigationElement;
      }
    }
    List<PsiElement> elementsToSearch = new ArrayList<>(overrides.length + 1);
    elementsToSearch.add(parameter);
    int idx = ReadAction.compute(() -> method.getParameterList().getParameterIndex(parameter));
    for (PsiMethod override : overrides) {
      final PsiParameter[] parameters = override.getParameterList().getParameters();
      if (idx < parameters.length) {
        elementsToSearch.add(parameters[idx]);
      }
    }

    final PsiClass aClass = ReadAction.compute(method::getContainingClass);
    if (aClass != null) {
      FunctionalExpressionSearch.search(aClass).forEach(element -> {
        if (element instanceof PsiLambdaExpression) {
          PsiParameter[] parameters = ((PsiLambdaExpression)element).getParameterList().getParameters();
          if (idx < parameters.length) {
            elementsToSearch.add(parameters[idx]);
          }
        }
      });
    }

    return PsiUtilCore.toPsiElementArray(elementsToSearch);
  }


  @Override
  public PsiElement @NotNull [] getPrimaryElements() {
    final PsiElement element = getPsiElement();
    if (element instanceof PsiParameter) {
      final PsiParameter parameter = (PsiParameter)element;
      final PsiElement scope = parameter.getDeclarationScope();
      if (scope instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)scope;
        if (PsiUtil.canBeOverridden(method)) {
          final PsiClass aClass = method.getContainingClass();
          LOG.assertTrue(aClass != null); //Otherwise can not be overridden

          Boolean hasOverridden = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
            return OverridingMethodsSearch.search(method).findFirst() != null ||
                   FunctionalExpressionSearch.search(aClass).findFirst() != null;
          }, JavaBundle.message("progress.title.detect.overridden.methods"), true, getProject());
          if (hasOverridden != null && hasOverridden.booleanValue() && askWhetherShouldSearchForParameterInOverridingMethods(element, parameter)) {
            return ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> getParameterElementsToSearch(parameter, method), JavaBundle.message("progress.title.detect.overridden.methods"), true, getProject()) ;
          }
        }
      }
    }
    return myElementsToSearch.length == 0 ? new PsiElement[]{element} : myElementsToSearch;
  }

  @Override
  public PsiElement @NotNull [] getSecondaryElements() {
    PsiElement element = getPsiElement();
    if (ApplicationManager.getApplication().isUnitTestMode()) return PsiElement.EMPTY_ARRAY;
    if (element instanceof PsiField) {
      final PsiField field = (PsiField)element;
      PsiClass containingClass = field.getContainingClass();
      if (containingClass != null) {
        String fieldName = field.getName();
        final String propertyName = JavaCodeStyleManager.getInstance(getProject()).variableNameToPropertyName(fieldName, VariableKind.FIELD);
        Set<PsiMethod> accessors = new HashSet<>();
        boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
        Collection<PsiMethod> methods = Arrays.asList(containingClass.getMethods());
        PsiMethod getter = PropertyUtilBase.findPropertyGetterWithType(propertyName, isStatic, field.getType(), methods);
        if (getter != null) accessors.add(getter);
        PsiMethod setter = PropertyUtilBase.findPropertySetterWithType(propertyName, isStatic, field.getType(), methods);
        if (setter != null) accessors.add(setter);
        accessors.addAll(PropertyUtilBase.getAccessors(containingClass, fieldName));
        accessors.removeIf(accessor -> field != PropertyUtilBase.findPropertyFieldByMember(accessor));
        if (!accessors.isEmpty()) {
          boolean containsPhysical = ContainerUtil.find(accessors, psiMethod -> psiMethod.isPhysical()) != null;
          final boolean doSearch = !containsPhysical || askShouldSearchAccessors(fieldName);
          if (doSearch) {
            final Set<PsiElement> elements = new HashSet<>();
            for (PsiMethod accessor : accessors) {
              ContainerUtil.addAll(elements, SuperMethodWarningUtil.checkSuperMethods(accessor, getActionString()));
            }
            return PsiUtilCore.toPsiElementArray(elements);
          }
        }
      }
    }
    return super.getSecondaryElements();
  }

  public static boolean askShouldSearchAccessors(@NotNull String fieldName) {
    int ret = Messages.showOkCancelDialog(JavaBundle.message("find.field.accessors.prompt", fieldName),
                                        JavaBundle.message("find.field.accessors.title"),
                                        JavaBundle.message("include.accessors"),
                                        JavaBundle.message("exclude.accessors"), Messages.getQuestionIcon());
    return ret == Messages.OK;
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
                                      @NotNull final Processor<? super UsageInfo> processor,
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
      Set<PsiMethod> superTargets = new LinkedHashSet<>();
      PsiMethod[] superMethods = ((PsiMethod)target).findDeepestSuperMethods();
      if (superMethods.length == 0) {
        superTargets.add((PsiMethod)target);
      }
      if (searchScope instanceof LocalSearchScope) {
        PsiElement[] scopeElements = ((LocalSearchScope)searchScope).getScope();
        GlobalSearchScope resolveScope =
          GlobalSearchScope.union(ContainerUtil.map2Array(scopeElements, GlobalSearchScope.class, PsiElement::getResolveScope));
        for (HierarchicalMethodSignature superSignature : PsiSuperMethodImplUtil.getHierarchicalMethodSignature((PsiMethod)target, resolveScope)
          .getSuperSignatures()) {
          PsiMethod method = superSignature.getMethod();
          PsiMethod[] deepestSupers = method.findDeepestSuperMethods();
          Collections.addAll(superTargets, deepestSupers.length == 0 ? new PsiMethod[]{method} : deepestSupers);
        }
      } else {
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

  @NotNull
  protected static String getActionString() {
    return FindBundle.message("find.super.method.warning.action.verb");
  }
}
