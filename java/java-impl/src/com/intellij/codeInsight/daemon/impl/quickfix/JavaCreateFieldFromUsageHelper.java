/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceField.BaseExpressionToFieldHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Max Medvedev
 */
public class JavaCreateFieldFromUsageHelper extends CreateFieldFromUsageHelper {

  @Override
  public Template setupTemplateImpl(PsiField field,
                                    Object expectedTypes,
                                    PsiClass targetClass,
                                    Editor editor,
                                    PsiElement context,
                                    boolean createConstantField,
                                    PsiSubstitutor substitutor) {
    Project project = field.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

    field = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(field);
    TemplateBuilderImpl builder = new TemplateBuilderImpl(field);
    if (!(expectedTypes instanceof ExpectedTypeInfo[])) {
      expectedTypes = ExpectedTypeInfo.EMPTY_ARRAY;
    }
    new GuessTypeParameters(factory).setupTypeElement(field.getTypeElement(), (ExpectedTypeInfo[])expectedTypes, substitutor, builder,
                                                      context, targetClass);

    if (createConstantField) {
      field.setInitializer(factory.createExpressionFromText("0", null));
      builder.replaceElement(field.getInitializer(), new EmptyExpression());
      PsiIdentifier identifier = field.getNameIdentifier();
      builder.setEndVariableAfter(identifier);
      field = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(field);
    }
    editor.getCaretModel().moveToOffset(field.getTextRange().getStartOffset());
    Template template = builder.buildInlineTemplate();
    if (ExpectedTypesProvider.processExpectedTypes((ExpectedTypeInfo[])expectedTypes, new PsiTypeVisitor<PsiType>() {
      @Nullable
      @Override
      public PsiType visitType(PsiType type) {
        return type;
      }
    }, project).length > 1) template.setToShortenLongNames(false);
    return template;
  }

  @Override
  public PsiField insertFieldImpl(@NotNull PsiClass targetClass, @NotNull PsiField field, @Nullable PsiElement place) {
    PsiMember enclosingContext = null;
    PsiClass parentClass;
    do {
      enclosingContext = PsiTreeUtil.getParentOfType(enclosingContext == null ? place : enclosingContext, PsiMethod.class, PsiField.class, PsiClassInitializer.class);
      parentClass = enclosingContext == null ? null : enclosingContext.getContainingClass();
    }
    while (parentClass instanceof PsiAnonymousClass);

    return BaseExpressionToFieldHandler.ConvertToFieldRunnable.appendField(targetClass, field, enclosingContext, null);
  }

}
