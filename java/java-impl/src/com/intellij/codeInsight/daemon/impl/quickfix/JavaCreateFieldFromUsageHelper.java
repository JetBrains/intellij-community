// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
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
                                    @NotNull PsiSubstitutor substitutor) {
    Project project = field.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

    field = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(field);
    TemplateBuilderImpl builder = new TemplateBuilderImpl(field);
    if (!(expectedTypes instanceof ExpectedTypeInfo[])) {
      expectedTypes = ExpectedTypeInfo.EMPTY_ARRAY;
    }
    new GuessTypeParameters(project, factory, builder, substitutor).setupTypeElement(
      field.getTypeElement(), (ExpectedTypeInfo[])expectedTypes, context, targetClass
    );

    if (createConstantField && !field.hasInitializer()) {
      field.setInitializer(factory.createExpressionFromText("0", null));
      builder.replaceElement(field.getInitializer(), new EmptyExpression());
      PsiIdentifier identifier = field.getNameIdentifier();
      builder.setEndVariableAfter(identifier);
    }

    field = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(field);

    editor.getCaretModel().moveToOffset(field.getTextRange().getStartOffset());
    Template template = builder.buildInlineTemplate();
    if (disableShortenLongNames(project, ((ExpectedTypeInfo[])expectedTypes))) {
      template.setToShortenLongNames(false);
    }
    return template;
  }

  private static boolean disableShortenLongNames(Project project, ExpectedTypeInfo[] expectedTypes) {
    if (Registry.is("ide.create.field.enable.shortening")) return false;
    return ExpectedTypesProvider.processExpectedTypes(expectedTypes, new PsiTypeVisitor<>() {
      @Override
      public PsiType visitType(@NotNull PsiType type) {
        return type;
      }
    }, project).length > 1;
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

    return CommonJavaRefactoringUtil.appendField(targetClass, field, enclosingContext, null);
  }

}
