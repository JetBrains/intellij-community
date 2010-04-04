/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.refactoring.changeSignature;

import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.JavaUnresolvableLocalCollisionDetector;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.refactoring.util.*;
import com.intellij.refactoring.util.usageInfo.DefaultConstructorImplicitUsageInfo;
import com.intellij.refactoring.util.usageInfo.NoConstructorClassUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class JavaChangeSignatureUsageProcessor implements ChangeSignatureUsageProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.changeSignature.JavaChangeSignatureUsageProcessor");

  private static boolean isJavaUsage(UsageInfo info) {
    final PsiElement element = info.getElement();
    if (element == null) return false;
    return element.getLanguage() == StdLanguages.JAVA;
  }

  public UsageInfo[] findUsages(ChangeInfo info) {
    if (info instanceof JavaChangeInfo) {
      return new UsageSearcher((JavaChangeInfo)info).findUsages();
    }
    else {
      return UsageInfo.EMPTY_ARRAY;
    }
  }

  public MultiMap<PsiElement, String> findConflicts(ChangeInfo info, Ref<UsageInfo[]> refUsages) {
    if (info instanceof JavaChangeInfo) {
      return new ConflictSearcher((JavaChangeInfo)info).findConflicts(refUsages);
    }
    else {
      return new MultiMap<PsiElement, String>();
    }
  }

  public boolean processUsage(ChangeInfo changeInfo, UsageInfo usageInfo) {
    if (!isJavaUsage(usageInfo)) return false;

    return true;
  }

  private static class UsageSearcher {
    private final JavaChangeInfo myChangeInfo;

    private UsageSearcher(JavaChangeInfo changeInfo) {
      this.myChangeInfo = changeInfo;
    }

    public UsageInfo[] findUsages() {
      ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();
      final PsiElement element = myChangeInfo.getMethod();
      if (element instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)element;

        findSimpleUsages(method, result);

        final UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
        return UsageViewUtil.removeDuplicatedUsages(usageInfos);
      }
      return UsageInfo.EMPTY_ARRAY;
    }


    private void findSimpleUsages(final PsiMethod method, final ArrayList<UsageInfo> result) {
      PsiMethod[] overridingMethods = findSimpleUsagesWithoutParameters(method, result, true, true, true);
      findUsagesInCallers(result);

      //Parameter name changes are not propagated
      findParametersUsage(method, result, overridingMethods);
    }

    private void findUsagesInCallers(final ArrayList<UsageInfo> usages) {
      if (myChangeInfo instanceof JavaChangeInfoImpl) {
        JavaChangeInfoImpl changeInfo = (JavaChangeInfoImpl)myChangeInfo;

        for (PsiMethod caller : changeInfo.propagateParametersMethods) {
          usages.add(new CallerUsageInfo(caller, true, changeInfo.propagateExceptionsMethods.contains(caller)));
        }
        for (PsiMethod caller : changeInfo.propagateExceptionsMethods) {
          usages.add(new CallerUsageInfo(caller, changeInfo.propagateParametersMethods.contains(caller), true));
        }
        Set<PsiMethod> merged = new HashSet<PsiMethod>();
        merged.addAll(changeInfo.propagateParametersMethods);
        merged.addAll(changeInfo.propagateExceptionsMethods);
        for (final PsiMethod method : merged) {
          findSimpleUsagesWithoutParameters(method, usages, changeInfo.propagateParametersMethods.contains(method),
                                            changeInfo.propagateExceptionsMethods.contains(method), false);
        }
      }
    }

    private void detectLocalsCollisionsInMethod(final PsiMethod method, final ArrayList<UsageInfo> result, boolean isOriginal) {
      if (myChangeInfo.getLanguage().equals(StdLanguages.JAVA)) {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        final Set<PsiParameter> deletedOrRenamedParameters = new HashSet<PsiParameter>();
        if (isOriginal) {
          deletedOrRenamedParameters.addAll(Arrays.asList(parameters));
          for (ParameterInfo parameterInfo : myChangeInfo.getNewParameters()) {
            if (parameterInfo.getOldIndex() >= 0) {
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
          if (oldParameterIndex >= 0) {
            if (isOriginal) {   //Name changes take place only in primary method
              final PsiParameter parameter = parameters[oldParameterIndex];
              if (!newName.equals(parameter.getName())) {
                JavaUnresolvableLocalCollisionDetector.visitLocalsCollisions(parameter, newName, method.getBody(), null,
                                                                             new JavaUnresolvableLocalCollisionDetector.CollidingVariableVisitor() {
                                                                               public void visitCollidingElement(final PsiVariable collidingVariable) {
                                                                                 if (!deletedOrRenamedParameters
                                                                                   .contains(collidingVariable)) {
                                                                                   result.add(
                                                                                     new RenamedParameterCollidesWithLocalUsageInfo(
                                                                                       parameter, collidingVariable, method));
                                                                                 }
                                                                               }
                                                                             });
              }
            }
          }
          else {
            JavaUnresolvableLocalCollisionDetector.visitLocalsCollisions(method, newName, method.getBody(), null,
                                                                         new JavaUnresolvableLocalCollisionDetector.CollidingVariableVisitor() {
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
    }

    private void findParametersUsage(final PsiMethod method, ArrayList<UsageInfo> result, PsiMethod[] overriders) {
      if (StdLanguages.JAVA.equals(myChangeInfo.getLanguage())) {
        PsiParameter[] parameters = method.getParameterList().getParameters();
        for (ParameterInfo info : myChangeInfo.getNewParameters()) {
          if (info.getOldIndex() >= 0) {
            PsiParameter parameter = parameters[info.getOldIndex()];
            if (!info.getName().equals(parameter.getName())) {
              addParameterUsages(parameter, result, info);

              for (PsiMethod overrider : overriders) {
                PsiParameter parameter1 = overrider.getParameterList().getParameters()[info.getOldIndex()];
                if (parameter.getName().equals(parameter1.getName())) {
                  addParameterUsages(parameter1, result, info);
                }
              }
            }
          }
        }
      }
    }

    private static boolean shouldPropagateToNonPhysicalMethod(PsiMethod method,
                                                              ArrayList<UsageInfo> result,
                                                              PsiClass containingClass,
                                                              final Set<PsiMethod> propagateMethods) {
      for (PsiMethod psiMethod : propagateMethods) {
        if (!psiMethod.isPhysical() && Comparing.strEqual(psiMethod.getName(), containingClass.getName())) {
          result.add(new DefaultConstructorImplicitUsageInfo(psiMethod, containingClass, method));
          return true;
        }
      }
      return false;
    }

    private PsiMethod[] findSimpleUsagesWithoutParameters(final PsiMethod method,
                                                          final ArrayList<UsageInfo> result,
                                                          boolean isToModifyArgs,
                                                          boolean isToThrowExceptions,
                                                          boolean isOriginal) {

      GlobalSearchScope projectScope = GlobalSearchScope.projectScope(method.getProject());
      PsiMethod[] overridingMethods = OverridingMethodsSearch.search(method, method.getUseScope(), true).toArray(PsiMethod.EMPTY_ARRAY);

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
              if (!method.isVarArgs() && list.getExpressions().length != parameterCount) continue;
            }
          }
          if (RefactoringUtil.isMethodUsage(element)) {
            result.add(new MethodCallUsageInfo(element, isToModifyArgs, isToCatchExceptions));
          }
          else if (element instanceof PsiDocTagValue) {
            result.add(new UsageInfo(ref.getElement()));
          }
          else if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) {
            DefaultConstructorImplicitUsageInfo implicitUsageInfo =
              new DefaultConstructorImplicitUsageInfo((PsiMethod)element, ((PsiMethod)element).getContainingClass(), method);
            result.add(implicitUsageInfo);
          }
          else if (element instanceof PsiClass) {
            LOG.assertTrue(method.isConstructor());
            final PsiClass psiClass = (PsiClass)element;
            if (!(myChangeInfo instanceof JavaChangeInfoImpl)) continue;
            if (shouldPropagateToNonPhysicalMethod(method, result, psiClass,
                                                   ((JavaChangeInfoImpl)myChangeInfo).propagateParametersMethods)) {
              continue;
            }
            if (shouldPropagateToNonPhysicalMethod(method, result, psiClass,
                                                   ((JavaChangeInfoImpl)myChangeInfo).propagateExceptionsMethods)) {
              continue;
            }
            result.add(new NoConstructorClassUsageInfo(psiClass));
          }
          else if (ref instanceof PsiCallReference) {
            result.add(new CallReferenceUsageInfo((PsiCallReference)ref));
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
        }
      }

      // Conflicts
      detectLocalsCollisionsInMethod(method, result, isOriginal);
      for (final PsiMethod overridingMethod : overridingMethods) {
        detectLocalsCollisionsInMethod(overridingMethod, result, isOriginal);
      }

      return overridingMethods;
    }


    private static void addParameterUsages(PsiParameter parameter, ArrayList<UsageInfo> results, ParameterInfo info) {
      PsiManager manager = parameter.getManager();
      GlobalSearchScope projectScope = GlobalSearchScope.projectScope(manager.getProject());
      for (PsiReference psiReference : ReferencesSearch.search(parameter, projectScope, false)) {
        PsiElement parmRef = psiReference.getElement();
        UsageInfo usageInfo = new MyParameterUsageInfo(parmRef, parameter.getName(), info.getName());
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

      public RenamedParameterCollidesWithLocalUsageInfo(PsiParameter parameter, PsiElement collidingElement, PsiMethod method) {
        super(parameter, collidingElement);
        myCollidingElement = collidingElement;
        myMethod = method;
      }

      public String getDescription() {
        return RefactoringBundle.message("there.is.already.a.0.in.the.1.it.will.conflict.with.the.renamed.parameter",
                                         RefactoringUIUtil.getDescription(myCollidingElement, true),
                                         RefactoringUIUtil.getDescription(myMethod, true));
      }
    }
  }

  private static class ConflictSearcher {
    private final JavaChangeInfo myChangeInfo;

    private ConflictSearcher(JavaChangeInfo changeInfo) {
      this.myChangeInfo = changeInfo;
    }

    public MultiMap<PsiElement, String> findConflicts(Ref<UsageInfo[]> refUsages) {
      MultiMap<PsiElement, String> conflictDescriptions = new MultiMap<PsiElement, String>();
      addMethodConflicts(conflictDescriptions);
      UsageInfo[] usagesIn = refUsages.get();
      RenameUtil.addConflictDescriptions(usagesIn, conflictDescriptions);
      Set<UsageInfo> usagesSet = new HashSet<UsageInfo>(Arrays.asList(usagesIn));
      RenameUtil.removeConflictUsages(usagesSet);
      if (myChangeInfo.isVisibilityChanged()) {
        try {
          addInaccessibilityDescriptions(usagesSet, conflictDescriptions);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      return conflictDescriptions;
    }

    private boolean needToChangeCalls() {
      return myChangeInfo.isNameChanged() || myChangeInfo.isParameterSetOrOrderChanged() || myChangeInfo.isExceptionSetOrOrderChanged();
    }


    private void addInaccessibilityDescriptions(Set<UsageInfo> usages, MultiMap<PsiElement, String> conflictDescriptions) throws IncorrectOperationException {
      PsiMethod method = myChangeInfo.getMethod();
      PsiModifierList modifierList = (PsiModifierList)method.getModifierList().copy();
      VisibilityUtil.setVisibility(modifierList, myChangeInfo.getNewVisibility());

      for (Iterator<UsageInfo> iterator = usages.iterator(); iterator.hasNext();) {
        UsageInfo usageInfo = iterator.next();
        PsiElement element = usageInfo.getElement();
        if (element != null) {
          if (element instanceof PsiReferenceExpression) {
            PsiClass accessObjectClass = null;
            PsiExpression qualifier = ((PsiReferenceExpression)element).getQualifierExpression();
            if (qualifier != null) {
              accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass(qualifier).getElement();
            }

            if (!JavaPsiFacade.getInstance(element.getProject()).getResolveHelper()
              .isAccessible(method, modifierList, element, accessObjectClass, null)) {
              String message =
                RefactoringBundle.message("0.with.1.visibility.is.not.accesible.from.2",
                                          RefactoringUIUtil.getDescription(method, true),
                                          myChangeInfo.getNewVisibility(),
                                          RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(element), true));
              conflictDescriptions.putValue(method, message);
              if (!needToChangeCalls()) {
                iterator.remove();
              }
            }
          }
        }
      }
    }


    private void addMethodConflicts(MultiMap<PsiElement, String> conflicts) {
      String newMethodName = myChangeInfo.getNewName();
      if (!(myChangeInfo instanceof JavaChangeInfo)) {
        return;
      }
      try {
        PsiMethod prototype;
        final PsiMethod method = myChangeInfo.getMethod();
        PsiManager manager = PsiManager.getInstance(method.getProject());
        PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        final CanonicalTypes.Type returnType = myChangeInfo.getNewReturnType();
        if (returnType != null) {
          prototype = factory.createMethod(newMethodName, returnType.getType(method, manager));
        }
        else {
          prototype = factory.createConstructor();
          prototype.setName(newMethodName);
        }
        JavaParameterInfo[] parameters = myChangeInfo.getNewParameters();


        for (JavaParameterInfo info : parameters) {
          final PsiType parameterType = info.createType(method, manager);
          PsiParameter param = factory.createParameter(info.getName(), parameterType);
          prototype.getParameterList().add(param);
        }

        ConflictsUtil.checkMethodConflicts(method.getContainingClass(), method, prototype, conflicts);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  static class MyParameterUsageInfo extends UsageInfo {
    final String oldParameterName;
    final String newParameterName;

    public MyParameterUsageInfo(PsiElement element, String oldParameterName, String newParameterName) {
      super(element);
      this.oldParameterName = oldParameterName;
      this.newParameterName = newParameterName;
    }
  }
}
