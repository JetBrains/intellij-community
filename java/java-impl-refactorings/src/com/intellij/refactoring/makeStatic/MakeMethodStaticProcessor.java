// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.makeStatic;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.java.JavaBundle;
import com.intellij.model.BranchableUsageInfo;
import com.intellij.model.ModelBranch;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.changeSignature.JavaChangeInfoImpl;
import com.intellij.refactoring.changeSignature.JavaChangeSignatureUsageProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.refactoring.changeSignature.inCallers.JavaCallerChooser;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.javadoc.MethodJavaDocHelper;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MakeMethodStaticProcessor extends MakeMethodOrClassStaticProcessor<PsiMethod> {
  private static final Logger LOG = Logger.getInstance(MakeMethodStaticProcessor.class);
  private @Nullable List<PsiMethod> myAdditionalMethods;

  public MakeMethodStaticProcessor(final Project project, final PsiMethod method, final Settings settings) {
    super(project, method, settings);
  }

  @Override
  protected boolean findAdditionalMembers(final Set<UsageInfo> toMakeStatic) {
    if (!toMakeStatic.isEmpty()) {
      myAdditionalMethods = new ArrayList<>();
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        for (UsageInfo usageInfo : toMakeStatic) {
          myAdditionalMethods.add((PsiMethod)usageInfo.getElement());
        }
      }
      else {
        final JavaCallerChooser chooser = new MakeStaticJavaCallerChooser(myMember, myProject,
                                                                          methods -> myAdditionalMethods.addAll(methods)) {
          @Override
          protected ArrayList<UsageInfo> getTopLevelItems() {
            return new ArrayList<>(toMakeStatic);
          }
        };
        TreeUtil.expand(chooser.getTree(), 2);
        if (!chooser.showAndGet()) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  protected MultiMap<PsiElement, String> getConflictDescriptions(UsageInfo[] usages) {
    MultiMap<PsiElement, String> descriptions = super.getConflictDescriptions(usages);
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element instanceof PsiMethodReferenceExpression && needLambdaConversion((PsiMethodReferenceExpression)element)) {
        descriptions.putValue(element, JavaBundle.message("refactoring.method.reference.to.lambda.conflict"));
      }
    }
    return descriptions;
  }

  @Override
  protected void changeSelfUsage(SelfUsageInfo usageInfo) throws IncorrectOperationException {
    PsiElement element = usageInfo.getElement();
    PsiElement parent = element.getParent();
    if (element instanceof PsiMethodReferenceExpression) {
      if (needLambdaConversion((PsiMethodReferenceExpression)element)) {
        PsiMethodCallExpression methodCallExpression = getMethodCallExpression((PsiMethodReferenceExpression)element);
        if (methodCallExpression == null) return;
        parent = methodCallExpression;
      }
      else {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(parent.getProject());
        PsiClass memberClass = myMember.getContainingClass();
        LOG.assertTrue(memberClass != null);
        PsiElement qualifier = ((PsiMethodReferenceExpression)element).getQualifier();
        LOG.assertTrue(qualifier != null);
        qualifier.replace(factory.createReferenceExpression(memberClass));
        return;
      }
    }
    LOG.assertTrue(parent instanceof PsiMethodCallExpression, parent);
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression) parent;
    final PsiExpression qualifier = methodCall.getMethodExpression().getQualifierExpression();
    if (qualifier != null) qualifier.delete();

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(methodCall.getProject());
    PsiExpressionList args = methodCall.getArgumentList();
    PsiElement addParameterAfter = null;

    if(mySettings.isMakeClassParameter()) {
      PsiElement arg = factory.createExpressionFromText(mySettings.getClassParameterName(), null);
      addParameterAfter = args.addAfter(arg, null);
    }

    if(mySettings.isMakeFieldParameters()) {
      List<Settings.FieldParameter> parameters = mySettings.getParameterOrderList();
      for (Settings.FieldParameter fieldParameter : parameters) {
        PsiElement arg = factory.createExpressionFromText(fieldParameter.name, null);
        if (addParameterAfter == null) {
          addParameterAfter = args.addAfter(arg, null);
        }
        else {
          addParameterAfter = args.addAfter(arg, addParameterAfter);
        }
      }
    }
  }

  @Override
  protected void changeSelf(PsiElementFactory factory, UsageInfo[] usages)
          throws IncorrectOperationException {
    final MethodJavaDocHelper javaDocHelper = new MethodJavaDocHelper(myMember);
    PsiParameterList paramList = myMember.getParameterList();
    PsiElement addParameterAfter = null;
    PsiDocTag anchor = null;

    final PsiClass containingClass = myMember.getContainingClass();
    LOG.assertTrue(containingClass != null);

    if (mySettings.isDelegate()) {
      List<ParameterInfoImpl> params = new ArrayList<>();
      PsiParameter[] parameters = myMember.getParameterList().getParameters();

      if (mySettings.isMakeClassParameter()) {
        params.add(ParameterInfoImpl.createNew()
                     .withName(mySettings.getClassParameterName())
                     .withType(factory.createType(containingClass, PsiSubstitutor.EMPTY))
                     .withDefaultValue("this"));
      }

      if (mySettings.isMakeFieldParameters()) {
        for (Settings.FieldParameter parameter : mySettings.getParameterOrderList()) {
          params.add(ParameterInfoImpl.createNew()
                       .withName(mySettings.getClassParameterName())
                       .withType(parameter.type)
                       .withDefaultValue(parameter.field.getName()));
        }
      }

      for (int i = 0; i < parameters.length; i++) {
        params.add(ParameterInfoImpl.create(i));
      }

      final PsiType returnType = myMember.getReturnType();
      LOG.assertTrue(returnType != null);
      JavaChangeSignatureUsageProcessor.generateDelegate(new JavaChangeInfoImpl(VisibilityUtil.getVisibilityModifier(myMember.getModifierList()),
                                                                                myMember,
                                                                                myMember.getName(),
                                                                                CanonicalTypes.createTypeWrapper(returnType),
                                                                                params.toArray(new ParameterInfoImpl[0]),
                                                                                new ThrownExceptionInfo[0],
                                                                                false,
                                                                                Collections.emptySet(),
                                                                                Collections.emptySet()));
    }

    if (mySettings.isMakeClassParameter()) {
      // Add parameter for object
      PsiType parameterType = factory.createType(containingClass, PsiSubstitutor.EMPTY);

      final String classParameterName = mySettings.getClassParameterName();
      PsiParameter parameter = factory.createParameter(classParameterName, parameterType);
      if(makeClassParameterFinal(usages)) {
        PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL, true);
      }
      addParameterAfter = paramList.addAfter(parameter, null);
      anchor = javaDocHelper.addParameterAfter(classParameterName, null);
    }

    if (mySettings.isMakeFieldParameters()) {
      List<Settings.FieldParameter> parameters = mySettings.getParameterOrderList();

      for (Settings.FieldParameter fieldParameter : parameters) {
        final PsiType fieldParameterType = fieldParameter.field.getType();
        final PsiParameter parameter = factory.createParameter(fieldParameter.name, fieldParameterType);
        if (makeFieldParameterFinal(fieldParameter.field, usages)) {
          PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL, true);
        }
        addParameterAfter = paramList.addAfter(parameter, addParameterAfter);
        anchor = javaDocHelper.addParameterAfter(fieldParameter.name, anchor);
      }
    }
    makeStatic(myMember);

    if (myAdditionalMethods != null) {
      for (PsiMethod method : myAdditionalMethods) {
        makeStatic(method);
      }
    }
  }

  private void makeStatic(PsiMethod member) {
    final PsiAnnotation overrideAnnotation = AnnotationUtil.findAnnotation(member, CommonClassNames.JAVA_LANG_OVERRIDE);
    if (overrideAnnotation != null) {
      overrideAnnotation.delete();
    }
    setupTypeParameterList(member);
    // Add static modifier
    final PsiModifierList modifierList = member.getModifierList();
    modifierList.setModifierProperty(PsiModifier.STATIC, true);
    modifierList.setModifierProperty(PsiModifier.FINAL, false);
    modifierList.setModifierProperty(PsiModifier.DEFAULT, false);

    PsiReceiverParameter receiverParameter = PsiTreeUtil.getChildOfType(member.getParameterList(), PsiReceiverParameter.class);
    if (receiverParameter != null) {
      receiverParameter.delete();
    }
  }

  @Override
  protected void changeInternalUsage(InternalUsageInfo usage, PsiElementFactory factory)
          throws IncorrectOperationException {
    if (!mySettings.isChangeSignature()) return;

    PsiElement element = usage.getElement();

    if (element instanceof PsiReferenceExpression) {
      PsiReferenceExpression newRef = null;

      if (mySettings.isMakeFieldParameters()) {
        PsiElement resolved = ((PsiReferenceExpression) element).resolve();
        if (resolved instanceof PsiField) {
          String name = mySettings.getNameForField((PsiField) resolved);
          if (name != null) {
            newRef = (PsiReferenceExpression) factory.createExpressionFromText(name, null);
          }
        }
      }

      if (newRef == null && mySettings.isMakeClassParameter()) {
        newRef =
        (PsiReferenceExpression) factory.createExpressionFromText(
                mySettings.getClassParameterName() + "." + element.getText(), null);
      }

      if (newRef != null) {
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myProject);
        newRef = (PsiReferenceExpression) codeStyleManager.reformat(newRef);
        element.replace(newRef);
      }
    }
    else if (element instanceof PsiThisExpression && mySettings.isMakeClassParameter()) {
      element.replace(factory.createExpressionFromText(mySettings.getClassParameterName(), null));
    }
    else if (element instanceof PsiSuperExpression && mySettings.isMakeClassParameter()) {
      element.replace(factory.createExpressionFromText(mySettings.getClassParameterName(), null));
    }
    else if (element instanceof PsiNewExpression newExpression && mySettings.isMakeClassParameter()) {
      LOG.assertTrue(newExpression.getQualifier() == null);
      final String newText = mySettings.getClassParameterName() + "." + newExpression.getText();
      final PsiExpression expr = factory.createExpressionFromText(newText, null);
      element.replace(expr);
    }
  }

  @Override
  protected void changeExternalUsage(UsageInfo usage, PsiElementFactory factory)
          throws IncorrectOperationException {
    final PsiElement element = usage.getElement();
    if (!(element instanceof PsiReferenceExpression ref)) return;

    if (ref instanceof PsiMethodReferenceExpression methodRef && needLambdaConversion(methodRef)) {
      PsiMethodCallExpression expression = getMethodCallExpression(methodRef);
      if (expression == null) return;
      ref = expression.getMethodExpression();
    }
    PsiElement parent = ref.getParent();

    PsiExpression instanceRef;

    instanceRef = ref.getQualifierExpression();
    PsiElement newQualifier;

    final PsiClass memberClass = myMember.getContainingClass();
    if (instanceRef == null || instanceRef instanceof PsiSuperExpression) {
      PsiClass contextClass = PsiTreeUtil.getParentOfType(ref, PsiClass.class);
      if (!InheritanceUtil.isInheritorOrSelf(contextClass, memberClass, true)) {
        instanceRef = factory.createExpressionFromText(memberClass.getQualifiedName() + ".this", null);
      } else {
        instanceRef = factory.createExpressionFromText("this", null);
      }
      newQualifier = null;
    }
    else {
      newQualifier = memberClass == null || memberClass instanceof PsiAnonymousClass ? null : factory.createReferenceExpression(memberClass);
    }

    if (mySettings.getNewParametersNumber() > 1) {
      int copyingSafetyLevel = RefactoringUtil.verifySafeCopyExpression(instanceRef);
      if (copyingSafetyLevel == RefactoringUtil.EXPR_COPY_PROHIBITED) {
        String tempVar = RefactoringUtil.createTempVar(instanceRef, parent, true);
        instanceRef = factory.createExpressionFromText(tempVar, null);
      }
    }


    PsiElement anchor = null;
    PsiExpressionList argList = null;
    PsiExpression[] exprs = PsiExpression.EMPTY_ARRAY;
    if (parent instanceof PsiMethodCallExpression) {
      argList = ((PsiMethodCallExpression)parent).getArgumentList();
      exprs = argList.getExpressions();
      if (mySettings.isMakeClassParameter()) {
        if (exprs.length > 0) {
          anchor = argList.addBefore(instanceRef, exprs[0]);
        }
        else {
          anchor = argList.add(instanceRef);
        }
      }
    }


    if (mySettings.isMakeFieldParameters()) {
      List<Settings.FieldParameter> parameters = mySettings.getParameterOrderList();

      for (Settings.FieldParameter fieldParameter : parameters) {
        PsiReferenceExpression fieldRef;
        if (newQualifier != null) {
          fieldRef = (PsiReferenceExpression)factory.createExpressionFromText(
            "a." + fieldParameter.field.getName(), null);
          fieldRef.getQualifierExpression().replace(instanceRef);
        }
        else {
          fieldRef = (PsiReferenceExpression)factory.createExpressionFromText(fieldParameter.field.getName(), null);
        }

        if (anchor != null) {
          anchor = argList.addAfter(fieldRef, anchor);
        }
        else if (argList != null) {
          if (exprs.length > 0) {
            anchor = argList.addBefore(fieldRef, exprs[0]);
          }
          else {
            anchor = argList.add(fieldRef);
          }
        }
      }
    }

    if (newQualifier != null) {
      ref.getQualifierExpression().replace(newQualifier);
    }
  }

  private static PsiMethodCallExpression getMethodCallExpression(PsiMethodReferenceExpression methodRef) {
    PsiLambdaExpression lambdaExpression =
      LambdaRefactoringUtil.convertMethodReferenceToLambda(methodRef, true, true);
    List<PsiExpression> returnExpressions = LambdaUtil.getReturnExpressions(lambdaExpression);
    if (returnExpressions.size() != 1) return null;
    PsiExpression expression = returnExpressions.get(0);
    if (!(expression instanceof PsiMethodCallExpression)) return null;
    return (PsiMethodCallExpression)expression;
  }

  private boolean needLambdaConversion(PsiMethodReferenceExpression methodRef) {
    if (myMember.getContainingClass() instanceof PsiAnonymousClass) {
      return true;
    }
    if (mySettings.isMakeFieldParameters()) {
      return true;
    }
    if (PsiMethodReferenceUtil.isResolvedBySecondSearch(methodRef)) {
      return myMember.getParameters().length != 0 || !mySettings.isMakeClassParameter();
    }
    return mySettings.isMakeClassParameter();
  }

  @Override
  protected void findExternalUsages(final ArrayList<UsageInfo> result) {
    if (mySettings.isDelegate()) return;
    findExternalReferences(myMember, result);
  }

  @Override
  protected void processExternalReference(PsiElement element, PsiMethod method, ArrayList<UsageInfo> result) {
    if (!mySettings.isChangeSignature()) {
      final PsiMethod containingMethod = MakeStaticJavaCallerChooser.isTheLastClassRef(element, method);
      if (containingMethod != null && !TestFrameworks.getInstance().isTestMethod(containingMethod)) {
        result.add(new ChainedCallUsageInfo(containingMethod));
      }
    }
  }

  @Override
  protected void performRefactoringInBranch(UsageInfo @NotNull [] usages, ModelBranch branch) {
    MakeMethodStaticProcessor processor = new MakeMethodStaticProcessor(
      myProject, branch.obtainPsiCopy(myMember), mySettings.obtainBranchCopy(branch));
    if (myAdditionalMethods != null) {
      processor.myAdditionalMethods = ContainerUtil.map(myAdditionalMethods, branch::obtainPsiCopy);
    }
    UsageInfo[] convertedUsages = BranchableUsageInfo.convertUsages(usages, branch);
    processor.performRefactoring(convertedUsages);
  }

  @Override
  protected boolean canPerformRefactoringInBranch() {
    return true;
  }
}
