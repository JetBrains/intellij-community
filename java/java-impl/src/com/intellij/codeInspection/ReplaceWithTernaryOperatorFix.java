// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.java.JavaBundle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

public class ReplaceWithTernaryOperatorFix implements LocalQuickFix {
  private final String myText;

  @Override
  @NotNull
  public String getName() {
    return JavaBundle.message("inspection.replace.ternary.quickfix", myText);
  }

  public ReplaceWithTernaryOperatorFix(@NotNull PsiExpression expressionToAssert) {
    myText = ParenthesesUtils.getText(expressionToAssert, ParenthesesUtils.BINARY_AND_PRECEDENCE);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("inspection.surround.if.family");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    while (true) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiCallExpression || parent instanceof PsiJavaCodeReferenceElement) {
        element = parent;
      }
      else {
        break;
      }
    }
    if (!(element instanceof PsiExpression expression)) {
      return;
    }

    final PsiFile file = expression.getContainingFile();
    PsiConditionalExpression conditionalExpression =
      replaceWithConditionalExpression(project, myText + "!=null", expression, suggestDefaultValue(expression));

    selectElseBranch(file, conditionalExpression);
  }

  static void selectElseBranch(PsiFile file, PsiConditionalExpression conditionalExpression) {
    if (!file.isPhysical()) return;
    PsiExpression elseExpression = conditionalExpression.getElseExpression();
    if (elseExpression != null) {
      Project project = file.getProject();
      Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
      if (editor != null) {
        Document document = editor.getDocument();
        PsiFile topLevelFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(file);
        if (topLevelFile != null && document == topLevelFile.getViewProvider().getDocument()) {
          PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
          TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(elseExpression);
          builder.replaceElement(elseExpression, new ConstantNode(elseExpression.getText()));
          builder.run(editor, true);
        }
      }
    }
  }

  @NotNull
  private static PsiConditionalExpression replaceWithConditionalExpression(@NotNull Project project,
                                                                           @NotNull String condition,
                                                                           @NotNull PsiExpression expression,
                                                                           @NotNull String defaultValue) {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

    final PsiElement parent = expression.getParent();
    final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)factory.createExpressionFromText(
      condition + " ? " + expression.getText() + " : " + defaultValue,
      parent
    );

    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    return (PsiConditionalExpression)expression.replace( codeStyleManager.reformat(conditionalExpression));
  }

  public static boolean isAvailable(@NotNull PsiExpression qualifier, @NotNull PsiExpression expression) {
    if (!qualifier.isValid() || qualifier.getText() == null) {
      return false;
    }

    return !(expression.getParent() instanceof PsiExpressionStatement) && !PsiUtil.isAccessedForWriting(expression);
  }

  private static String suggestDefaultValue(@NotNull PsiExpression expression) {
    PsiType type = expression.getType();
    return PsiTypesUtil.getDefaultValueOfType(type);
  }

  public static class ReplaceMethodRefWithTernaryOperatorFix implements LocalQuickFix {
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.replace.methodref.ternary.quickfix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodReferenceExpression element = ObjectUtils.tryCast(descriptor.getPsiElement(), PsiMethodReferenceExpression.class);
      if (element == null) return;
      PsiLambdaExpression lambda =
        LambdaRefactoringUtil.convertMethodReferenceToLambda(element, false, true);
      if (lambda == null) return;
      PsiExpression expression = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
      if (expression == null) return;
      PsiParameter parameter = ArrayUtil.getFirstElement(lambda.getParameterList().getParameters());
      if (parameter == null) return;
      String text = parameter.getName();
      final PsiFile file = expression.getContainingFile();
      PsiConditionalExpression conditionalExpression = replaceWithConditionalExpression(project, text + "!=null", expression,
                                                                                        suggestDefaultValue(expression));

      selectElseBranch(file, conditionalExpression);
    }
  }
}
