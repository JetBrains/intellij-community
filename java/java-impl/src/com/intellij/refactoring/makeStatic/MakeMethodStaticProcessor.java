/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring.makeStatic;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.javadoc.MethodJavaDocHelper;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dsl
 */
public class MakeMethodStaticProcessor extends MakeMethodOrClassStaticProcessor<PsiMethod> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.makeMethodStatic.MakeMethodStaticProcessor");

  public MakeMethodStaticProcessor(final Project project, final PsiMethod method, final Settings settings) {
    super(project, method, settings);
  }

  protected void changeSelfUsage(SelfUsageInfo usageInfo) throws IncorrectOperationException {
    PsiElement parent = usageInfo.getElement().getParent();
    LOG.assertTrue(parent instanceof PsiMethodCallExpression);
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression) parent;
    final PsiExpression qualifier = methodCall.getMethodExpression().getQualifierExpression();
    if (qualifier != null) qualifier.delete();

    PsiElementFactory factory = JavaPsiFacade.getInstance(methodCall.getProject()).getElementFactory();
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

  protected void changeSelf(PsiElementFactory factory, UsageInfo[] usages)
          throws IncorrectOperationException {
    final MethodJavaDocHelper javaDocHelper = new MethodJavaDocHelper(myMember);
    PsiParameterList paramList = myMember.getParameterList();
    PsiElement addParameterAfter = null;
    PsiDocTag anchor = null;
    List<PsiType> addedTypes = new ArrayList<PsiType>();

    if (mySettings.isMakeClassParameter()) {
      // Add parameter for object
      PsiType parameterType = factory.createType(myMember.getContainingClass(), PsiSubstitutor.EMPTY);
      addedTypes.add(parameterType);

      final String classParameterName = mySettings.getClassParameterName();
      PsiParameter parameter = factory.createParameter(classParameterName, parameterType);
      if(makeClassParameterFinal(usages)) {
        PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL, true);
      }
      addParameterAfter = paramList.addAfter(parameter, null);
      anchor = javaDocHelper.addParameterAfter(classParameterName, anchor);
    }

    if (mySettings.isMakeFieldParameters()) {
      List<Settings.FieldParameter> parameters = mySettings.getParameterOrderList();

      for (Settings.FieldParameter fieldParameter : parameters) {
        final PsiType fieldParameterType = fieldParameter.field.getType();
        final PsiParameter parameter = factory.createParameter(fieldParameter.name, fieldParameterType);
        addedTypes.add(fieldParameterType);
        if (makeFieldParameterFinal(fieldParameter.field, usages)) {
          PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL, true);
        }
        addParameterAfter = paramList.addAfter(parameter, addParameterAfter);
        anchor = javaDocHelper.addParameterAfter(fieldParameter.name, anchor);
      }
    }
    setupTypeParameterList();
    // Add static modifier
    final PsiModifierList modifierList = myMember.getModifierList();
    modifierList.setModifierProperty(PsiModifier.STATIC, true);
    modifierList.setModifierProperty(PsiModifier.FINAL, false);
  }

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
    else if (element instanceof PsiNewExpression && mySettings.isMakeClassParameter()) {
      final PsiNewExpression newExpression = ((PsiNewExpression)element);
      LOG.assertTrue(newExpression.getQualifier() == null);
      final String newText = mySettings.getClassParameterName() + "." + newExpression.getText();
      final PsiExpression expr = factory.createExpressionFromText(newText, null);
      element.replace(expr);
    }
  }

  protected void changeExternalUsage(UsageInfo usage, PsiElementFactory factory)
          throws IncorrectOperationException {
    final PsiElement element = usage.getElement();
    if (!(element instanceof PsiReferenceExpression)) return;

    PsiReferenceExpression methodRef = (PsiReferenceExpression) element;
    PsiElement parent = methodRef.getParent();

    PsiExpression instanceRef;

    instanceRef = methodRef.getQualifierExpression();
    PsiElement newQualifier;

    final PsiClass memberClass = myMember.getContainingClass();
    if (instanceRef == null || instanceRef instanceof PsiSuperExpression) {
      PsiClass contextClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      if (!InheritanceUtil.isInheritorOrSelf(contextClass, memberClass, true)) {
        instanceRef = factory.createExpressionFromText(memberClass.getQualifiedName() + ".this", null);
      } else {
        instanceRef = factory.createExpressionFromText("this", null);
      }
      newQualifier = null;
    }
    else {
      newQualifier = factory.createReferenceExpression(memberClass);
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
    PsiExpression[] exprs = new PsiExpression[0];
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
        else {
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
      methodRef.getQualifierExpression().replace(newQualifier);
    }
  }

  protected void findExternalUsages(final ArrayList<UsageInfo> result) {
    findExternalReferences(myMember, result);
  }
}
