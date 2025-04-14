// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.ide.util.EditorHelper;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodViewDescriptor;
import com.intellij.refactoring.util.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

public final class ConvertToInstanceMethodProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(ConvertToInstanceMethodProcessor.class);
  private PsiMethod myMethod;
  private @Nullable PsiParameter myTargetParameter;
  private final boolean myParameterIsReassigned;
  private PsiClass myTargetClass;
  private Map<PsiTypeParameter, PsiTypeParameter> myTypeParameterReplacements;
  private static final Key<PsiTypeParameter> BIND_TO_TYPE_PARAMETER = Key.create("REPLACEMENT");
  private final String myOldVisibility;
  private final String myNewVisibility;


  public ConvertToInstanceMethodProcessor(final Project project,
                                          final PsiMethod method,
                                          final @Nullable PsiParameter targetParameter,
                                          final String newVisibility) {
    super(project);
    myMethod = method;
    myTargetParameter = targetParameter;
    myParameterIsReassigned = targetParameter != null && 
                              VariableAccessUtils.variableIsAssigned(myTargetParameter, myTargetParameter.getDeclarationScope());
    LOG.assertTrue(method.hasModifierProperty(PsiModifier.STATIC));
    if (myTargetParameter != null) {
      LOG.assertTrue(myTargetParameter.getDeclarationScope() == myMethod);
      LOG.assertTrue(myTargetParameter.getType() instanceof PsiClassType);
      final PsiType type = myTargetParameter.getType();
      LOG.assertTrue(type instanceof PsiClassType);
      myTargetClass = ((PsiClassType)type).resolve();
    }
    else {
      myTargetClass = method.getContainingClass();
    }
    myOldVisibility = VisibilityUtil.getVisibilityModifier(method.getModifierList());
    myNewVisibility = newVisibility;
  }

  public PsiClass getTargetClass() {
    return myTargetClass;
  }

  @Override
  protected @NotNull UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new MoveInstanceMethodViewDescriptor(myMethod, myTargetParameter, myTargetClass);
  }

  @Override
  protected void refreshElements(PsiElement @NotNull [] elements) {
    LOG.assertTrue(elements.length > 1);
    myMethod = (PsiMethod)elements[0];
    myTargetParameter = elements.length == 3 ? (PsiParameter)elements[1] : null;
    myTargetClass = (PsiClass)elements[elements.length - 1];
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    LOG.assertTrue(myTargetParameter == null || myTargetParameter.getDeclarationScope() == myMethod);
    final Project project = myMethod.getProject();

    final PsiReference[] methodReferences =
      ReferencesSearch.search(myMethod, GlobalSearchScope.projectScope(project), false).toArray(PsiReference.EMPTY_ARRAY);
    List<UsageInfo> result = new ArrayList<>();
    for (final PsiReference ref : methodReferences) {
      final PsiElement element = ref.getElement();
      if (element instanceof PsiReferenceExpression) {
        PsiElement parent = element.getParent();
        if (parent instanceof PsiMethodCallExpression) {
          result.add(new MethodCallUsageInfo((PsiMethodCallExpression)parent));
        }
        else if (element instanceof PsiMethodReferenceExpression) {
          result.add(new MethodReferenceUsageInfo((PsiMethodReferenceExpression)element, myTargetParameter == null || myMethod.getParameterList().getParameterIndex(myTargetParameter) == 0));
        }
      }
      else if (element instanceof PsiDocTagValue) {
        result.add(new JavaDocUsageInfo(ref)); //TODO:!!!
      }
    }

    if (myTargetParameter != null) {
      for (final PsiReference ref : ReferencesSearch.search(myTargetParameter, new LocalSearchScope(myMethod), false).asIterable()) {
        final PsiElement element = ref.getElement();
        if (element instanceof PsiReferenceExpression || element instanceof PsiDocParamRef) {
          result.add(new ParameterUsageInfo(ref));
        }
      }
    }

    if (myTargetClass.isInterface()) {
      PsiClass[] implementingClasses = RefactoringHierarchyUtil.findImplementingClasses(myTargetClass);
      for (final PsiClass implementingClass : implementingClasses) {
        result.add(new ImplementingClassUsageInfo(implementingClass));
      }
    }

    return result.toArray(UsageInfo.EMPTY_ARRAY);
  }

  @Override
  protected @NotNull String getRefactoringId() {
    return "refactoring.makeInstance";
  }

  @Override
  protected @NotNull RefactoringEventData getBeforeData() {
    RefactoringEventData data = new RefactoringEventData();
    data.addElements(new PsiElement[]{myMethod, myTargetClass});
    return data;
  }

  @Override
  protected @NotNull RefactoringEventData getAfterData(UsageInfo @NotNull [] usages) {
    RefactoringEventData data = new RefactoringEventData();
    data.addElement(myTargetClass);
    return data;
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usagesIn = refUsages.get();
    MultiMap<PsiElement, @DialogMessage String> conflicts = new MultiMap<>();
    final Set<PsiMember> methods = Collections.singleton(myMethod);
    //check that method to call would be still accessible from the call places
    RefactoringConflictsUtil.getInstance().analyzeAccessibilityConflictsAfterMemberMove(myTargetClass, myNewVisibility, methods, conflicts);
    //additionally check that body of method contains only accessible in the inheritors references
    if (myTargetClass.isInterface() && !PsiUtil.isAvailable(JavaFeature.EXTENSION_METHODS, myTargetClass)) {
      for (final UsageInfo usage : usagesIn) {
        if (usage instanceof ImplementingClassUsageInfo) {
          PsiClass targetClass = ((ImplementingClassUsageInfo)usage).getPsiClass();
          RefactoringConflictsUtilImpl.analyzeUsedElementsAfterMove(myMethod, myMethod, methods, null, targetClass, targetClass, conflicts);
        }
      }
    }

    for (final UsageInfo usageInfo : usagesIn) {
      PsiElement place = null;
      if (usageInfo instanceof MethodCallUsageInfo) {
        place = ((MethodCallUsageInfo)usageInfo).getMethodCall();
        if (myTargetParameter != null) {
          final PsiExpression[] expressions = ((PsiMethodCallExpression)place).getArgumentList().getExpressions();
          final int index = myMethod.getParameterList().getParameterIndex(myTargetParameter);
          if (index < expressions.length) {
            PsiExpression instanceValue = expressions[index];
            instanceValue = RefactoringUtil.unparenthesizeExpression(instanceValue);
            if (instanceValue instanceof PsiLiteralExpression && ((PsiLiteralExpression)instanceValue).getValue() == null) {
              String message = JavaRefactoringBundle.message("0.contains.call.with.null.argument.for.parameter.1",
                                                         RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(place), true),
                                                         CommonRefactoringUtil.htmlEmphasize(myTargetParameter.getName()));
              conflicts.putValue(place, message);
            }
          }
        }
      }
      else if (usageInfo instanceof MethodReferenceUsageInfo) {
        place = ((MethodReferenceUsageInfo)usageInfo).getExpression();
        if (!((MethodReferenceUsageInfo)usageInfo).isApplicableBySecondSearch()) {
          conflicts.putValue(place, JavaRefactoringBundle.message("expand.method.reference.warning"));
        }
      }

      if (myTargetParameter == null && place != null && myTargetClass.hasTypeParameters() && !thisAccessExpressionApplicable(place)) {
        conflicts.putValue(place, JavaRefactoringBundle.message("infer.class.type.args.warning", myTargetClass.getName()));
      }
    }

    return showConflicts(conflicts, usagesIn);
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    EditorHelper.openInEditor(doRefactoring(usages));
  }

  private @NotNull PsiMethod doRefactoring(UsageInfo[] usages) {
    myTypeParameterReplacements = buildTypeParameterReplacements();
    List<PsiClass> inheritors = new ArrayList<>();

    CommonRefactoringUtil.sortDepthFirstRightLeftOrder(usages);

    // Process usages
    for (final UsageInfo usage : usages) {
      if (usage instanceof MethodCallUsageInfo) {
        processMethodCall(((MethodCallUsageInfo)usage).getMethodCall());
      }
      else if (usage instanceof ParameterUsageInfo && !myParameterIsReassigned) {
        processParameterUsage((ParameterUsageInfo)usage);
      }
      else if (usage instanceof ImplementingClassUsageInfo) {
        inheritors.add(((ImplementingClassUsageInfo)usage).getPsiClass());
      }
      else if (usage instanceof MethodReferenceUsageInfo) {
        processMethodReference((MethodReferenceUsageInfo)usage);
      }
    }

    prepareTypeParameterReplacement();
    if (myTargetParameter != null) {
      if (myParameterIsReassigned) {
        PsiDeclarationStatement statement =
          JavaPsiFacade.getElementFactory(myProject).createVariableDeclarationStatement(myTargetParameter.getName(),
                                                                                        myTargetParameter.getType(),
                                                                                        createThisExpression());
        PsiCodeBlock body = myMethod.getBody();
        assert body != null;
        PsiElement first = body.getFirstBodyElement();
        assert first != null;
        first.getParent().addBefore(statement, first);
      }
      myTargetParameter.delete();
    }
    ChangeContextUtil.encodeContextInfo(myMethod, true);
    PsiMethod result;
    if (!myTargetClass.isInterface()) {
      result = addMethodToClass(myTargetClass);
      fixVisibility(result, usages);
    }
    else {
      result = addMethodToClass(myTargetClass);
      final PsiModifierList modifierList = result.getModifierList();
      final boolean markAsDefault = PsiUtil.isAvailable(JavaFeature.EXTENSION_METHODS, myTargetClass);
      if (markAsDefault) {
        modifierList.setModifierProperty(PsiModifier.DEFAULT, true);
      }
      RefactoringUtil.makeMethodAbstract(myTargetClass, result);

      if (!markAsDefault) {
        for (final PsiClass psiClass : inheritors) {
          final PsiMethod newMethod = addMethodToClass(psiClass);
          String modifier = myNewVisibility != null && !myNewVisibility.equals(VisibilityUtil.ESCALATE_VISIBILITY)
                            ? myNewVisibility
                            : PsiModifier.PUBLIC;
          PsiUtil.setModifierProperty(newMethod, modifier, true);
        }
      }
    }
    return result;
  }

  private void processMethodReference(MethodReferenceUsageInfo usage) {
    PsiMethodReferenceExpression expression = usage.getExpression();
    if (usage.isApplicableBySecondSearch()) {
      PsiExpression qualifierExpression = expression.getQualifierExpression();
      LOG.assertTrue(qualifierExpression != null);
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(myProject);
      PsiElement qualifier;
      if (myTargetParameter != null) {
        qualifier = factory.createReferenceExpression(myTargetClass);
      }
      else {
        boolean thisAccess = thisAccessExpressionApplicable(expression);
        qualifier = thisAccess ? createThisExpression() : createSyntheticAccessExpression(factory, expression);
      }
      qualifierExpression.replace(qualifier);
    }
    else {
      PsiLambdaExpression lambdaExpression = LambdaRefactoringUtil.convertMethodReferenceToLambda(expression, false, true);
      List<PsiExpression> returnExpressions = LambdaUtil.getReturnExpressions(lambdaExpression);
      if (!returnExpressions.isEmpty()) {
        PsiMethodCallExpression methodCall = (PsiMethodCallExpression)returnExpressions.get(0);
        processMethodCall(methodCall);
        usage.setReplacement(methodCall);
      }
    }
  }

  private void fixVisibility(final PsiMethod method, final UsageInfo[] usages) {
    final PsiModifierList modifierList = method.getModifierList();
    if (VisibilityUtil.ESCALATE_VISIBILITY.equals(myNewVisibility)) {
      for (UsageInfo usage : usages) {
        PsiElement place = null;
        if (usage instanceof MethodCallUsageInfo) {
          place = usage.getElement();
        }
        else if (usage instanceof MethodReferenceUsageInfo) {
          PsiMethodReferenceExpression expression = ((MethodReferenceUsageInfo)usage).getExpression();
          if (expression != null && expression.isValid()) {
            place = expression;
          }
          else {
            place = ((MethodReferenceUsageInfo)usage).getReplacement();
          }
        }
        if (place != null) {
          VisibilityUtil.escalateVisibility(method, place);
        }
      }
    }
    else if (myNewVisibility != null && !myNewVisibility.equals(myOldVisibility)) {
      modifierList.setModifierProperty(myNewVisibility, true);
    }
  }

  private void prepareTypeParameterReplacement() {
    if (myTypeParameterReplacements == null) return;
    final Collection<PsiTypeParameter> typeParameters = myTypeParameterReplacements.keySet();
    for (final PsiTypeParameter parameter : typeParameters) {
      for (final PsiReference reference : ReferencesSearch.search(parameter, new LocalSearchScope(myMethod), false).asIterable()) {
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

  private PsiMethod addMethodToClass(final PsiClass targetClass) {
    final PsiMethod newMethod;
    if (targetClass == myMethod.getContainingClass()) {
      newMethod = myMethod;
    }
    else {
      newMethod = (PsiMethod)targetClass.add(myMethod);
      PsiMethod copy = (PsiMethod)myMethod.copy();
      myMethod.delete();
      myMethod = copy;
    }
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
      @Override public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
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

  private void processParameterUsage(ParameterUsageInfo usage) {
    final PsiReference reference = usage.getReferenceExpression();
    if (reference instanceof PsiReferenceExpression referenceExpression) {
      if (referenceExpression.getParent() instanceof PsiReferenceExpression parentReference) {
        PsiExpression qualifier = calculateQualifier(parentReference);
        if (qualifier == null) referenceExpression.delete();
        else referenceExpression.replace(qualifier);
      }
      else {
        referenceExpression.replace(createThisExpression());
      }
    } else {
      final PsiElement element = reference.getElement();
      if (element instanceof PsiDocParamRef) {
        element.getParent().delete();
      }
    }
  }

  private PsiExpression calculateQualifier(PsiReferenceExpression parent) {
    if (parent instanceof PsiMethodReferenceExpression) {
      return createThisExpression();
    }
    if (parent.getParent() instanceof PsiMethodCallExpression methodCall) {
      final PsiMethodCallExpression copy = (PsiMethodCallExpression)methodCall.copy();
      final PsiExpression qualifier = copy.getMethodExpression().getQualifierExpression();
      if (qualifier != null) {
        qualifier.delete();
        JavaResolveResult resolveResult = copy.resolveMethodGenerics();
        if (methodCall.resolveMethod() == resolveResult.getElement() || !resolveResult.isValidResult()) {
          return null;
        }
        return createQualifiedThisExpression();
      }
    }
    else if (parent.resolve() instanceof PsiField field) {
      final PsiReferenceExpression copy = (PsiReferenceExpression)parent.copy();
      PsiExpression qualifier = copy.getQualifierExpression();
      if (qualifier != null) {
        qualifier.delete();
        PsiElement target = copy.resolve();
        if (target == field || target == null) {
          return null;
        }
        return target instanceof PsiField ? createQualifiedThisExpression() : createThisExpression();
      }
    }
    return null;
  }

  private @NotNull PsiExpression createQualifiedThisExpression() {
    return JavaPsiFacade.getElementFactory(myProject).createExpressionFromText(myTargetClass.getQualifiedName() + ".this", null);
  }

  private @NotNull PsiExpression createThisExpression() {
    return JavaPsiFacade.getElementFactory(myProject).createExpressionFromText(JavaKeywords.THIS, null);
  }

  private void processMethodCall(final PsiMethodCallExpression methodCall) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(myMethod.getProject());
    final PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
    PsiExpression argument;
    if (myTargetParameter != null) {
      PsiParameterList parameterList = myMethod.getParameterList();
      int parameterIndex = parameterList.getParameterIndex(myTargetParameter);
      PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
      if (arguments.length <= parameterIndex) return;
      argument = arguments[parameterIndex];
    }
    else {
      if (thisAccessExpressionApplicable(methodCall)) {
        PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
        if (qualifierExpression != null) {
          qualifierExpression.delete();
        }
        return;
      }

      argument = createSyntheticAccessExpression(factory, methodCall);
    }

    final PsiExpression qualifier;
    if (methodExpression.getQualifierExpression() != null) {
      qualifier = methodExpression.getQualifierExpression();
    }
    else {
      final PsiReferenceExpression newRefExpr = (PsiReferenceExpression)factory.createExpressionFromText("x." + myMethod.getName(), null);
      qualifier = ((PsiReferenceExpression)methodExpression.replace(newRefExpr)).getQualifierExpression();
    }
    qualifier.replace(argument);
    argument.delete();
  }

  private PsiExpression createSyntheticAccessExpression(PsiElementFactory factory, PsiElement context) {
    return factory.createExpressionFromText("new " + myTargetClass.getName() + "()", context);
  }

  private boolean thisAccessExpressionApplicable(PsiElement expression) {
    return PsiTreeUtil.isAncestor(myTargetClass, expression, false) && PsiUtil.getEnclosingStaticElement(expression, myTargetClass) == null;
  }

  @Override
  protected @NotNull String getCommandName() {
    return ConvertToInstanceMethodHandler.getRefactoringName();
  }

  public @Nullable Map<PsiTypeParameter, PsiTypeParameter> buildTypeParameterReplacements() {
    if (myTargetParameter == null) {
      return Collections.emptyMap();
    }
    final PsiClassType type = (PsiClassType)myTargetParameter.getType();
    final PsiSubstitutor substitutor = type.resolveGenerics().getSubstitutor();
    return calculateReplacementMap(substitutor, myTargetClass, myMethod);
  }

  private static @Nullable Map<PsiTypeParameter, PsiTypeParameter> calculateReplacementMap(final PsiSubstitutor substitutor,
                                                                                           final PsiClass targetClass,
                                                                                           final PsiElement containingElement) {
    final HashMap<PsiTypeParameter, PsiTypeParameter> result = new HashMap<>();
    for (PsiTypeParameter classTypeParameter : PsiUtil.typeParametersIterable(targetClass)) {
      if (!(substitutor.substitute(classTypeParameter) instanceof PsiClassType classType)) return null;
      if (!(classType.resolve() instanceof PsiTypeParameter methodTypeParameter)) return null;
      if (methodTypeParameter.getOwner() != containingElement) return null;
      if (result.containsKey(methodTypeParameter)) return null;
      result.put(methodTypeParameter, classTypeParameter);
    }
    return result;
  }

  public PsiMethod getMethod() {
    return myMethod;
  }

  public @Nullable PsiParameter getTargetParameter() {
    return myTargetParameter;
  }
}
