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
package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.util.EditorHelper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodViewDescriptor;
import com.intellij.refactoring.util.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author dsl
 */
public class ConvertToInstanceMethodProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG =
    Logger.getInstance("#com.intellij.refactoring.convertToInstanceMethod.ConvertToInstanceMethodProcessor");
  private PsiMethod myMethod;
  private PsiParameter myTargetParameter;
  private PsiClass myTargetClass;
  private Map<PsiTypeParameter, PsiTypeParameter> myTypeParameterReplacements;
  private static final Key<PsiTypeParameter> BIND_TO_TYPE_PARAMETER = Key.create("REPLACEMENT");
  private final String myOldVisibility;
  private final String myNewVisibility;


  public ConvertToInstanceMethodProcessor(final Project project,
                                          final PsiMethod method,
                                          final PsiParameter targetParameter,
                                          final String newVisibility) {
    super(project);
    myMethod = method;
    myTargetParameter = targetParameter;
    LOG.assertTrue(method.hasModifierProperty(PsiModifier.STATIC));
    LOG.assertTrue(myTargetParameter.getDeclarationScope() == myMethod);
    LOG.assertTrue(myTargetParameter.getType() instanceof PsiClassType);
    final PsiType type = myTargetParameter.getType();
    LOG.assertTrue(type instanceof PsiClassType);
    myTargetClass = ((PsiClassType)type).resolve();
    myOldVisibility = VisibilityUtil.getVisibilityModifier(method.getModifierList());
    myNewVisibility = newVisibility;
  }

  public PsiClass getTargetClass() {
    return myTargetClass;
  }

  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new MoveInstanceMethodViewDescriptor(myMethod, myTargetParameter, myTargetClass);
  }

  protected void refreshElements(@NotNull PsiElement[] elements) {
    LOG.assertTrue(elements.length == 3);
    myMethod = (PsiMethod)elements[0];
    myTargetParameter = (PsiParameter)elements[1];
    myTargetClass = (PsiClass)elements[2];
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    LOG.assertTrue(myTargetParameter.getDeclarationScope() == myMethod);
    final Project project = myMethod.getProject();

    final PsiReference[] methodReferences =
      ReferencesSearch.search(myMethod, GlobalSearchScope.projectScope(project), false).toArray(PsiReference.EMPTY_ARRAY);
    List<UsageInfo> result = new ArrayList<>();
    for (final PsiReference ref : methodReferences) {
      final PsiElement element = ref.getElement();
      if (element instanceof PsiReferenceExpression) {
        if (element.getParent() instanceof PsiMethodCallExpression) {
          result.add(new MethodCallUsageInfo((PsiMethodCallExpression)element.getParent()));
        }
      }
      else if (element instanceof PsiDocTagValue) {
        result.add(new JavaDocUsageInfo(ref)); //TODO:!!!
      }
    }

    for (final PsiReference ref : ReferencesSearch.search(myTargetParameter, new LocalSearchScope(myMethod), false)) {
      final PsiElement element = ref.getElement();
      if (element instanceof PsiReferenceExpression || element instanceof PsiDocParamRef) {
        result.add(new ParameterUsageInfo(ref));
      }
    }

    if (myTargetClass.isInterface()) {
      PsiClass[] implementingClasses = RefactoringHierarchyUtil.findImplementingClasses(myTargetClass);
      for (final PsiClass implementingClass : implementingClasses) {
        result.add(new ImplementingClassUsageInfo(implementingClass));
      }
    }


    return result.toArray(new UsageInfo[result.size()]);
  }


  @Nullable
  @Override
  protected String getRefactoringId() {
    return "refactoring.makeInstance";
  }

  @Nullable
  @Override
  protected RefactoringEventData getBeforeData() {
    RefactoringEventData data = new RefactoringEventData();
    data.addElements(new PsiElement[]{myMethod, myTargetClass});
    return data;
  }

  @Nullable
  @Override
  protected RefactoringEventData getAfterData(@NotNull UsageInfo[] usages) {
    RefactoringEventData data = new RefactoringEventData();
    data.addElement(myTargetClass);
    return data;
  }

  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usagesIn = refUsages.get();
    MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    final Set<PsiMember> methods = Collections.singleton((PsiMember)myMethod);
    if (!myTargetClass.isInterface()) {
      RefactoringConflictsUtil.analyzeAccessibilityConflicts(methods, myTargetClass, conflicts, myNewVisibility);
    }
    else {
      for (final UsageInfo usage : usagesIn) {
        if (usage instanceof ImplementingClassUsageInfo) {
          RefactoringConflictsUtil
            .analyzeAccessibilityConflicts(methods, ((ImplementingClassUsageInfo)usage).getPsiClass(), conflicts, PsiModifier.PUBLIC);
        }
      }
    }

    for (final UsageInfo usageInfo : usagesIn) {
      if (usageInfo instanceof MethodCallUsageInfo) {
        final PsiMethodCallExpression methodCall = ((MethodCallUsageInfo)usageInfo).getMethodCall();
        final PsiExpression[] expressions = methodCall.getArgumentList().getExpressions();
        final int index = myMethod.getParameterList().getParameterIndex(myTargetParameter);
        if (index < expressions.length) {
          PsiExpression instanceValue = expressions[index];
          instanceValue = RefactoringUtil.unparenthesizeExpression(instanceValue);
          if (instanceValue instanceof PsiLiteralExpression && ((PsiLiteralExpression)instanceValue).getValue() == null) {
            String message = RefactoringBundle.message("0.contains.call.with.null.argument.for.parameter.1",
                                                       RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(methodCall), true),
                                                       CommonRefactoringUtil.htmlEmphasize(myTargetParameter.getName()));
            conflicts.putValue(methodCall, message);
          }
        }
      }
    }

    return showConflicts(conflicts, usagesIn);
  }

  protected void performRefactoring(@NotNull UsageInfo[] usages) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, myTargetClass)) return;
    LocalHistoryAction a = LocalHistory.getInstance().startAction(getCommandName());
    try {
      doRefactoring(usages);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    finally {
      a.finish();
    }
  }

  private void doRefactoring(UsageInfo[] usages) throws IncorrectOperationException {
    myTypeParameterReplacements = buildTypeParameterReplacements();
    List<PsiClass> inheritors = new ArrayList<>();

    CommonRefactoringUtil.sortDepthFirstRightLeftOrder(usages);

    // Process usages
    for (final UsageInfo usage : usages) {
      if (usage instanceof MethodCallUsageInfo) {
        processMethodCall((MethodCallUsageInfo)usage);
      }
      else if (usage instanceof ParameterUsageInfo) {
        processParameterUsage((ParameterUsageInfo)usage);
      }
      else if (usage instanceof ImplementingClassUsageInfo) {
        inheritors.add(((ImplementingClassUsageInfo)usage).getPsiClass());
      }
    }

    prepareTypeParameterReplacement();
    myTargetParameter.delete();
    ChangeContextUtil.encodeContextInfo(myMethod, true);
    if (!myTargetClass.isInterface()) {
      PsiMethod method = addMethodToClass(myTargetClass);
      fixVisibility(method, usages);
      EditorHelper.openInEditor(method);
    }
    else {
      final PsiMethod interfaceMethod = addMethodToClass(myTargetClass);
      final PsiModifierList modifierList = interfaceMethod.getModifierList();
      final boolean markAsDefault = PsiUtil.isLanguageLevel8OrHigher(myTargetClass);
      if (markAsDefault) {
        modifierList.setModifierProperty(PsiModifier.DEFAULT, true);
      }
      RefactoringUtil.makeMethodAbstract(myTargetClass, interfaceMethod);

      EditorHelper.openInEditor(interfaceMethod);

      if (!markAsDefault) {
        for (final PsiClass psiClass : inheritors) {
          final PsiMethod newMethod = addMethodToClass(psiClass);
          PsiUtil.setModifierProperty(newMethod, myNewVisibility != null && !myNewVisibility.equals(VisibilityUtil.ESCALATE_VISIBILITY) ? myNewVisibility
                                                                                                                                        : PsiModifier.PUBLIC, true);
        }
      }
    }
    myMethod.delete();
  }

  private void fixVisibility(final PsiMethod method, final UsageInfo[] usages) throws IncorrectOperationException {
    final PsiModifierList modifierList = method.getModifierList();
    if (VisibilityUtil.ESCALATE_VISIBILITY.equals(myNewVisibility)) {
      for (UsageInfo usage : usages) {
        if (usage instanceof MethodCallUsageInfo) {
          final PsiElement place = usage.getElement();
          if (place != null) {
            VisibilityUtil.escalateVisibility(method, place);
          }
        }
      }
    }
    else if (myNewVisibility != null && !myNewVisibility.equals(myOldVisibility)) {
      modifierList.setModifierProperty(myNewVisibility, true);
    }
  }

  private void prepareTypeParameterReplacement() throws IncorrectOperationException {
    if (myTypeParameterReplacements == null) return;
    final Collection<PsiTypeParameter> typeParameters = myTypeParameterReplacements.keySet();
    for (final PsiTypeParameter parameter : typeParameters) {
      for (final PsiReference reference : ReferencesSearch.search(parameter, new LocalSearchScope(myMethod), false)) {
        if (reference.getElement() instanceof PsiJavaCodeReferenceElement) {
          reference.getElement().putCopyableUserData(BIND_TO_TYPE_PARAMETER, myTypeParameterReplacements.get(parameter));
        }
      }
    }
    final Set<PsiTypeParameter> methodTypeParameters = myTypeParameterReplacements.keySet();
    for (final PsiTypeParameter methodTypeParameter : methodTypeParameters) {
      methodTypeParameter.delete();
    }
  }

  private PsiMethod addMethodToClass(final PsiClass targetClass) throws IncorrectOperationException {
    final PsiMethod newMethod = (PsiMethod)targetClass.add(myMethod);
    final PsiModifierList modifierList = newMethod.getModifierList();
    modifierList.setModifierProperty(PsiModifier.STATIC, false);
    ChangeContextUtil.decodeContextInfo(newMethod, null, null);
    if (myTypeParameterReplacements == null) return newMethod;
    final Map<PsiTypeParameter, PsiTypeParameter> additionalReplacements;
    if (targetClass != myTargetClass) {
      final PsiSubstitutor superClassSubstitutor =
        TypeConversionUtil.getSuperClassSubstitutor(myTargetClass, targetClass, PsiSubstitutor.EMPTY);
      final Map<PsiTypeParameter, PsiTypeParameter> map = calculateReplacementMap(superClassSubstitutor, myTargetClass, targetClass);
      if (map == null) return newMethod;
      additionalReplacements = new HashMap<>();
      for (final Map.Entry<PsiTypeParameter, PsiTypeParameter> entry : map.entrySet()) {
        additionalReplacements.put(entry.getValue(), entry.getKey());
      }
    }
    else {
      additionalReplacements = null;
    }
    newMethod.accept(new JavaRecursiveElementVisitor() {
      @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        PsiTypeParameter typeParameterToBind = reference.getCopyableUserData(BIND_TO_TYPE_PARAMETER);
        if (typeParameterToBind != null) {
          reference.putCopyableUserData(BIND_TO_TYPE_PARAMETER, null);
          try {
            if (additionalReplacements != null) {
              typeParameterToBind = additionalReplacements.get(typeParameterToBind);
            }
            reference.bindToElement(typeParameterToBind);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
        else {
          visitElement(reference);
        }
      }
    });
    return newMethod;
  }

  private void processParameterUsage(ParameterUsageInfo usage) throws IncorrectOperationException {
    final PsiReference reference = usage.getReferenceExpression();
    if (reference instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)reference;
      PsiElement parent = referenceExpression.getParent();
      if (parent instanceof PsiReferenceExpression && sameUnqualified(parent)) {
        referenceExpression.delete();
      }
      else {
        final PsiExpression expression =
          JavaPsiFacade.getInstance(myMethod.getProject()).getElementFactory().createExpressionFromText("this", null);
        referenceExpression.replace(expression);
      }
    } else {
      final PsiElement element = reference.getElement();
      if (element instanceof PsiDocParamRef) {
        element.getParent().delete();
      }
    }
  }

  private static boolean sameUnqualified(PsiElement parent) {
    PsiElement resolve = ((PsiReferenceExpression)parent).resolve();
    if (resolve instanceof PsiField) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(resolve.getProject());
      final PsiExpression unqualifiedFieldReference = elementFactory.createExpressionFromText(((PsiField)resolve).getName(), parent);
      return resolve == ((PsiReferenceExpression)unqualifiedFieldReference).resolve();
    }
    return true;
  }

  private void processMethodCall(MethodCallUsageInfo usageInfo) throws IncorrectOperationException {
    PsiMethodCallExpression methodCall = usageInfo.getMethodCall();
    PsiParameterList parameterList = myMethod.getParameterList();
    PsiElementFactory factory = JavaPsiFacade.getInstance(myMethod.getProject()).getElementFactory();
    int parameterIndex = parameterList.getParameterIndex(myTargetParameter);
    PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
    if (arguments.length <= parameterIndex) return;
    final PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
    final PsiExpression qualifier;
    if (methodExpression.getQualifierExpression() != null) {
      qualifier = methodExpression.getQualifierExpression();
    }
    else {
      final PsiReferenceExpression newRefExpr = (PsiReferenceExpression)factory.createExpressionFromText("x." + myMethod.getName(), null);
      qualifier = ((PsiReferenceExpression)methodExpression.replace(newRefExpr)).getQualifierExpression();
    }
    qualifier.replace(arguments[parameterIndex]);
    arguments[parameterIndex].delete();
  }

  protected String getCommandName() {
    return ConvertToInstanceMethodHandler.REFACTORING_NAME;
  }

  @Nullable
  public Map<PsiTypeParameter, PsiTypeParameter> buildTypeParameterReplacements() {
    final PsiClassType type = (PsiClassType)myTargetParameter.getType();
    final PsiSubstitutor substitutor = type.resolveGenerics().getSubstitutor();
    return calculateReplacementMap(substitutor, myTargetClass, myMethod);
  }

  @Nullable
  private static Map<PsiTypeParameter, PsiTypeParameter> calculateReplacementMap(final PsiSubstitutor substitutor,
                                                                          final PsiClass targetClass,
                                                                          final PsiElement containingElement) {
    final HashMap<PsiTypeParameter, PsiTypeParameter> result = new HashMap<>();
    for (PsiTypeParameter classTypeParameter : PsiUtil.typeParametersIterable(targetClass)) {
      final PsiType substitution = substitutor.substitute(classTypeParameter);
      if (!(substitution instanceof PsiClassType)) return null;
      final PsiClass aClass = ((PsiClassType)substitution).resolve();
      if (!(aClass instanceof PsiTypeParameter)) return null;
      final PsiTypeParameter methodTypeParameter = (PsiTypeParameter)aClass;
      if (methodTypeParameter.getOwner() != containingElement) return null;
      if (result.keySet().contains(methodTypeParameter)) return null;
      result.put(methodTypeParameter, classTypeParameter);
    }
    return result;
  }

  public PsiMethod getMethod() {
    return myMethod;
  }

  public PsiParameter getTargetParameter() {
    return myTargetParameter;
  }

}
