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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypeUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class CreateEnumConstantFromUsageFix extends CreateVarFromUsageFix implements HighPriorityAction{
  private static final Logger LOG = Logger.getInstance("com.intellij.codeInsight.daemon.impl.quickfix.CreateEnumConstantFromUsageFix");
  public CreateEnumConstantFromUsageFix(final PsiReferenceExpression referenceElement) {
    super(referenceElement);
  }

  @Override
  protected String getText(String varName) {
    return QuickFixBundle.message("create.enum.constant.from.usage.text", myReferenceExpression.getReferenceName());
  }

  @Override
  protected void invokeImpl(final PsiClass targetClass) {
    LOG.assertTrue(targetClass.isEnum());
    final String name = myReferenceExpression.getReferenceName();
    LOG.assertTrue(name != null);
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myReferenceExpression.getProject()).getElementFactory();
    PsiEnumConstant enumConstant = elementFactory.createEnumConstantFromText(name, null);
    enumConstant = (PsiEnumConstant)targetClass.add(enumConstant);

    final PsiMethod[] constructors = targetClass.getConstructors();
    if (constructors.length > 0) {
      final PsiMethod constructor = constructors[0];
      final PsiParameter[] parameters = constructor.getParameterList().getParameters();
      if (parameters.length > 0) {
        final String params = StringUtil.join(parameters, new Function<PsiParameter, String>() {
          @Override
          public String fun(PsiParameter psiParameter) {
            return psiParameter.getName();
          }
        }, ",");
        enumConstant = (PsiEnumConstant)enumConstant.replace(elementFactory.createEnumConstantFromText(name + "(" + params + ")", null));
        final TemplateBuilderImpl builder = new TemplateBuilderImpl(enumConstant);

        final PsiExpressionList argumentList = enumConstant.getArgumentList();
        LOG.assertTrue(argumentList != null);
        for (PsiExpression expression : argumentList.getExpressions()) {
          builder.replaceElement(expression, new EmptyExpression());
        }

        enumConstant = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(enumConstant);
        final Template template = builder.buildTemplate();

        final Project project = targetClass.getProject();
        final Editor newEditor = positionCursor(project, targetClass.getContainingFile(), enumConstant);
        if (newEditor != null) {
          final TextRange range = enumConstant.getTextRange();
          newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
          startTemplate(newEditor, template, project);
        }
      }
    }
  }

  @NotNull
  @Override
  protected List<PsiClass> getTargetClasses(PsiElement element) {
    final List<PsiClass> classes = super.getTargetClasses(element);
    PsiClass enumClass = null;
    for (PsiClass aClass : classes) {
      if (aClass.isEnum()) {
        if (enumClass == null) {
          enumClass = aClass;
        } else {
          enumClass = null;
          break;
        }
      }
    }

    if (enumClass != null) {
      return Collections.singletonList(enumClass);
    }
    ExpectedTypeInfo[] typeInfos = CreateFromUsageUtils.guessExpectedTypes(myReferenceExpression, false);
    for (final ExpectedTypeInfo typeInfo : typeInfos) {
      final PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(typeInfo.getType());
      if (psiClass != null && psiClass.isEnum()) {
        return Collections.singletonList(psiClass);
      }
    }
    return Collections.emptyList();
  }

  @Override
  protected boolean isAvailableImpl(int offset) {
    if (!super.isAvailableImpl(offset)) return false;
    PsiElement element = getElement();
    final List<PsiClass> classes = getTargetClasses(element);
    if (classes.size() != 1 || !classes.get(0).isEnum()) return false;
    ExpectedTypeInfo[] typeInfos = CreateFromUsageUtils.guessExpectedTypes(myReferenceExpression, false);
    PsiType enumType = JavaPsiFacade.getInstance(myReferenceExpression.getProject()).getElementFactory().createType(classes.get(0));
    for (final ExpectedTypeInfo typeInfo : typeInfos) {
      if (ExpectedTypeUtil.matches(enumType, typeInfo)) return true;
    }
    return false;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.constant.from.usage.family");
  }
}
