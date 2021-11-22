// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.changeSignature;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.JavaUnresolvableLocalCollisionDetector;
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.usageInfo.DefaultConstructorImplicitUsageInfo;
import com.intellij.refactoring.util.usageInfo.NoConstructorClassUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.VariableAccessUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
class JavaChangeSignatureUsageSearcher {
  private final JavaChangeInfo myChangeInfo;
  private static final Logger LOG = Logger.getInstance(JavaChangeSignatureUsageSearcher.class);

  JavaChangeSignatureUsageSearcher(JavaChangeInfo changeInfo) {
    this.myChangeInfo = changeInfo;
  }

  public UsageInfo[] findUsages() {
    ArrayList<UsageInfo> result = new ArrayList<>();
    final PsiMethod method = myChangeInfo.getMethod();

    findSimpleUsages(method, result);

    final UsageInfo[] usageInfos = result.toArray(UsageInfo.EMPTY_ARRAY);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }


  private void findSimpleUsages(final PsiMethod method, final ArrayList<? super UsageInfo> result) {
    PsiMethod[] overridingMethods = findSimpleUsagesWithoutParameters(method, result, true, true, true);
    findUsagesInCallers(result);

    final ArrayList<PsiMethod> methods = new ArrayList<>(Arrays.asList(overridingMethods));
    methods.add(method);

    for (PsiMethod psiMethod : methods) {
      for (PsiFunctionalExpression functionalExpression : FunctionalExpressionSearch.search(psiMethod)) {
        result.add(new FunctionalInterfaceChangedUsageInfo(functionalExpression, method));
      }
    }

    //Parameter name changes are not propagated
    findParametersUsage(method, result, overridingMethods);
  }

  private void findUsagesInCallers(final ArrayList<? super UsageInfo> usages) {
    if (myChangeInfo instanceof JavaChangeInfoImpl) {
      JavaChangeInfoImpl changeInfo = (JavaChangeInfoImpl)myChangeInfo;

      for (PsiMethod caller : changeInfo.propagateParametersMethods) {
        usages.add(new CallerUsageInfo(caller, true, changeInfo.propagateExceptionsMethods.contains(caller)));
      }
      for (PsiMethod caller : changeInfo.propagateExceptionsMethods) {
        usages.add(new CallerUsageInfo(caller, changeInfo.propagateParametersMethods.contains(caller), true));
      }
      Set<PsiMethod> merged = new HashSet<>();
      merged.addAll(changeInfo.propagateParametersMethods);
      merged.addAll(changeInfo.propagateExceptionsMethods);
      for (final PsiMethod method : merged) {
        findSimpleUsagesWithoutParameters(method, usages, changeInfo.propagateParametersMethods.contains(method),
                                          changeInfo.propagateExceptionsMethods.contains(method), false);
      }
    }
  }

  private void detectLocalsCollisionsInMethod(final PsiMethod method, final ArrayList<? super UsageInfo> result, boolean isOriginal) {
    if (!JavaLanguage.INSTANCE.equals(method.getLanguage())) return;

    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final Set<PsiParameter> deletedOrRenamedParameters = new HashSet<>();
    if (isOriginal) {
      ContainerUtil.addAll(deletedOrRenamedParameters, parameters);
      for (ParameterInfo parameterInfo : myChangeInfo.getNewParameters()) {
        if (parameterInfo.getOldIndex() >= 0 && parameterInfo.getOldIndex() < parameters.length) {
          final PsiParameter parameter = parameters[parameterInfo.getOldIndex()];
          if (parameterInfo.getName().equals(parameter.getName())) {
            deletedOrRenamedParameters.remove(parameter);
          }
        }
      }
    }

    for (ParameterInfo parameterInfo : myChangeInfo.getNewParameters()) {
      final int oldParameterIndex = parameterInfo.getOldIndex();
      final String newName = parameterInfo.getName();
      if (oldParameterIndex >= 0 ) {
        if (isOriginal && oldParameterIndex < parameters.length && !newName.equals(myChangeInfo.getOldParameterNames()[oldParameterIndex])) {
          //Name changes take place only in primary method when name was actually changed
          final PsiParameter parameter = parameters[oldParameterIndex];
          if (!newName.equals(parameter.getName())) {
            JavaUnresolvableLocalCollisionDetector.visitLocalsCollisions(
              parameter, newName, method.getBody(), null,
              new JavaUnresolvableLocalCollisionDetector.CollidingVariableVisitor() {
                @Override
                public void visitCollidingElement(final PsiVariable collidingVariable) {
                  if (!deletedOrRenamedParameters.contains(collidingVariable)) {
                    result.add(new RenamedParameterCollidesWithLocalUsageInfo(parameter, collidingVariable, method));
                  }
                }
              });
          }
        }
      }
      else {
        JavaUnresolvableLocalCollisionDetector.visitLocalsCollisions(
          method, newName, method.getBody(), null,
          new JavaUnresolvableLocalCollisionDetector.CollidingVariableVisitor() {
            @Override
            public void visitCollidingElement(PsiVariable collidingVariable) {
              if (!deletedOrRenamedParameters.contains(collidingVariable)) {
                result.add(new NewParameterCollidesWithLocalUsageInfo(
                  collidingVariable, collidingVariable, method));
              }
            }
          });
      }
    }
  }

  private void findParametersUsage(final PsiMethod method, ArrayList<? super UsageInfo> result, PsiMethod[] overriders) {
    if (JavaLanguage.INSTANCE.equals(myChangeInfo.getLanguage())) {
      PsiClass aClass = method.getContainingClass();
      PsiRecordComponent[] components = null;
      if (aClass != null && JavaPsiRecordUtil.isCanonicalConstructor(method)) {
        components = aClass.getRecordComponents();
      } 
      PsiParameter[] parameters = method.getParameterList().getParameters();
      for (ParameterInfo info : myChangeInfo.getNewParameters()) {
        if (info.getOldIndex() >= 0) {
          PsiParameter parameter = parameters[info.getOldIndex()];
          boolean nameChanged = !info.getName().equals(parameter.getName());
          if (nameChanged) {
            addParameterUsages(parameter, result, info);
            for (PsiMethod overrider : overriders) {
              PsiParameter parameter1 = overrider.getParameterList().getParameters()[info.getOldIndex()];
              if (parameter1 != null && Comparing.strEqual(parameter.getName(), parameter1.getName())) {
                addParameterUsages(parameter1, result, info);
              }
            }
          }
          if (components != null && components.length > info.getOldIndex()) {
            PsiRecordComponent component = components[info.getOldIndex()];
            if (nameChanged) {
              PsiField field = JavaPsiRecordUtil.getFieldForComponent(component);
              if (field != null) {
                for (PsiReferenceExpression reference : VariableAccessUtils.getVariableReferences(field, aClass)) {
                  UsageInfo usageInfo = new ChangeSignatureParameterUsageInfo(reference, parameter.getName(), info.getName());
                  result.add(usageInfo);
                }
              }
            }
            PsiMethod explicitGetter = ContainerUtil
              .find(aClass.findMethodsByName(parameter.getName(), false), m -> m.getParameterList().isEmpty());
            if (explicitGetter != null) {
              if (nameChanged) {
                addParameterUsages(explicitGetter, result, info);
              }
              if (!(explicitGetter instanceof LightRecordMethod) && 
                  (nameChanged || !parameter.getType().equalsToText(info.getTypeText()))) {
                result.add(new RecordGetterDeclarationUsageInfo(explicitGetter, info.getName(), info.getTypeText()));
              }
            }
          }
        }
      }
    }
  }

  private static boolean shouldPropagateToNonPhysicalMethod(PsiMethod method,
                                                            ArrayList<? super UsageInfo> result,
                                                            PsiClass containingClass,
                                                            final Set<? extends PsiMethod> propagateMethods) {
    for (PsiMethod psiMethod : propagateMethods) {
      if (!psiMethod.isPhysical() && Comparing.strEqual(psiMethod.getName(), containingClass.getName())) {
        result.add(new DefaultConstructorImplicitUsageInfo(psiMethod, containingClass, method));
        return true;
      }
    }
    return false;
  }

  private PsiMethod[] findSimpleUsagesWithoutParameters(final PsiMethod method,
                                                        final ArrayList<? super UsageInfo> result,
                                                        boolean isToModifyArgs,
                                                        boolean isToThrowExceptions,
                                                        boolean isOriginal) {

    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(method.getProject());
    PsiMethod[] overridingMethods = OverridingMethodsSearch.search(method).toArray(PsiMethod.EMPTY_ARRAY);

    for (PsiMethod overridingMethod : overridingMethods) {
      result.add(new OverriderUsageInfo(overridingMethod, method, isOriginal, isToModifyArgs, isToThrowExceptions));
    }

    boolean needToChangeCalls =
      !myChangeInfo.isGenerateDelegate() && (myChangeInfo.isNameChanged() ||
                                             myChangeInfo.isParameterSetOrOrderChanged() ||
                                             myChangeInfo.isExceptionSetOrOrderChanged() ||
                                             myChangeInfo.isVisibilityChanged()/*for checking inaccessible*/);
    if (needToChangeCalls) {
      int parameterCount = method.getParameterList().getParametersCount();

      PsiReference[] refs = MethodReferencesSearch.search(method, projectScope, true).toArray(PsiReference.EMPTY_ARRAY);
      for (PsiReference ref : refs) {
        PsiElement element = ref.getElement();

        boolean isToCatchExceptions = isToThrowExceptions && needToCatchExceptions(RefactoringUtil.getEnclosingMethod(element));
        if (!isToCatchExceptions) {
          if (RefactoringUtil.isMethodUsage(element)) {
            PsiExpressionList list = RefactoringUtil.getArgumentListByMethodReference(element);
            if (list == null || !method.isVarArgs() && list.getExpressionCount() != parameterCount) continue;
          }
        }
        if (RefactoringUtil.isMethodUsage(element)) {
          result.add(new MethodCallUsageInfo(element, isToModifyArgs, isToCatchExceptions));
        }
        else if (element instanceof PsiDocTagValue) {
          result.add(new UsageInfo(element));
        }
        else if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) {
          if (JavaLanguage.INSTANCE.equals(element.getLanguage())) {
            DefaultConstructorImplicitUsageInfo implicitUsageInfo =
              new DefaultConstructorImplicitUsageInfo((PsiMethod)element, ((PsiMethod)element).getContainingClass(), method);
            result.add(implicitUsageInfo);
          }
        }
        else if (element instanceof PsiClass) {
          LOG.assertTrue(method.isConstructor());
          final PsiClass psiClass = (PsiClass)element;
          if (JavaLanguage.INSTANCE.equals(psiClass.getLanguage())) {
            if (myChangeInfo instanceof JavaChangeInfoImpl) {
              if (shouldPropagateToNonPhysicalMethod(method, result, psiClass,
                                                     ((JavaChangeInfoImpl)myChangeInfo).propagateParametersMethods)) {
                continue;
              }
              if (shouldPropagateToNonPhysicalMethod(method, result, psiClass,
                                                     ((JavaChangeInfoImpl)myChangeInfo).propagateExceptionsMethods)) {
                continue;
              }
            }
            result.add(new NoConstructorClassUsageInfo(psiClass));
          }
        }
        else if (ref instanceof PsiCallReference) {
          result.add(new CallReferenceUsageInfo((PsiCallReference)ref));
        }
        else if (element instanceof PsiMethodReferenceExpression && MethodReferenceUsageInfo.needToExpand(myChangeInfo)) {
          result.add(new MethodReferenceUsageInfo(element, method, isToModifyArgs, isToCatchExceptions));
        }
        else {
          result.add(new MoveRenameUsageInfo(element, ref, method));
        }
      }

      //if (method.isConstructor() && parameterCount == 0) {
      //    RefactoringUtil.visitImplicitConstructorUsages(method.getContainingClass(),
      //                                                   new DefaultConstructorUsageCollector(result));
      //}
    }
    else if (myChangeInfo.isParameterTypesChanged()) {
      PsiReference[] refs = MethodReferencesSearch.search(method, projectScope, true).toArray(PsiReference.EMPTY_ARRAY);
      for (PsiReference reference : refs) {
        final PsiElement element = reference.getElement();
        if (element instanceof PsiDocTagValue) {
          result.add(new UsageInfo(reference));
        }
        else if (element instanceof XmlElement) {
          result.add(new MoveRenameUsageInfo(reference, method));
        }
        else if (element instanceof PsiMethodReferenceExpression) {
          result.add(new UsageInfo(reference));
        }
      }
    }

    // Conflicts
    detectLocalsCollisionsInMethod(method, result, isOriginal);
    for (final PsiMethod overridingMethod : overridingMethods) {
      detectLocalsCollisionsInMethod(overridingMethod, result, isOriginal);
    }

    return overridingMethods;
  }


  private static void addParameterUsages(PsiNamedElement parameter, ArrayList<? super UsageInfo> results, ParameterInfo info) {
    PsiManager manager = parameter.getManager();
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(manager.getProject());
    for (PsiReference psiReference : ReferencesSearch.search(parameter, projectScope, false)) {
      PsiElement parmRef = psiReference.getElement();
      UsageInfo usageInfo = new ChangeSignatureParameterUsageInfo(parmRef, parameter.getName(), info.getName());
      results.add(usageInfo);
    }
  }

  private boolean needToCatchExceptions(PsiMethod caller) {
    if (myChangeInfo instanceof JavaChangeInfoImpl) {
      return myChangeInfo.isExceptionSetOrOrderChanged() &&
             !((JavaChangeInfoImpl)myChangeInfo).propagateExceptionsMethods.contains(caller);
    }
    else {
      return myChangeInfo.isExceptionSetOrOrderChanged();
    }
  }

  private static class RenamedParameterCollidesWithLocalUsageInfo extends UnresolvableCollisionUsageInfo {
    private final PsiElement myCollidingElement;
    private final PsiMethod myMethod;

    RenamedParameterCollidesWithLocalUsageInfo(PsiParameter parameter, PsiElement collidingElement, PsiMethod method) {
      super(parameter, collidingElement);
      myCollidingElement = collidingElement;
      myMethod = method;
    }

    @Override
    public String getDescription() {
      return RefactoringBundle.message("there.is.already.a.0.in.the.1.it.will.conflict.with.the.renamed.parameter",
                                       RefactoringUIUtil.getDescription(myCollidingElement, true),
                                       RefactoringUIUtil.getDescription(myMethod, true));
    }
  }
}
