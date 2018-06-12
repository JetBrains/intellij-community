// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.selectorAllExpressionsWithCurrentOffset;

public class NewExpressionPostfixTemplate extends StringBasedPostfixTemplate {
  private static final Condition<PsiElement> CONSTRUCTOR = expression -> {
    if (!(expression instanceof PsiReferenceExpression) &&
        !(expression instanceof PsiMethodCallExpression)) {
      return false;
    }

    PsiReferenceExpression ref = expression instanceof PsiMethodCallExpression ?
                                 ((PsiMethodCallExpression)expression).getMethodExpression() :
                                 (PsiReferenceExpression)expression;

    PsiExpression qualifierExpression = ref.getQualifierExpression();

    //disabled for qualified elements
    //todo implement proper support for Foo<Bar>, Foo.new Bar() and java.util.Foo
    if (qualifierExpression != null) return false;

    JavaResolveResult result = ref.advancedResolve(true);

    PsiElement element = result.getElement();
    return element == null || element instanceof PsiClass;
  };

  protected NewExpressionPostfixTemplate() {
    super("new", "new T()", selectorAllExpressionsWithCurrentOffset(CONSTRUCTOR));
  }

  @Nullable
  @Override
  public String getTemplateString(@NotNull PsiElement element) {
    return element instanceof PsiMethodCallExpression ? "new $expr$" : "new $expr$($END$)";
  }

  @Override
  public void expandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    if (!(expression instanceof PsiReferenceExpression)) {
      super.expandForChooseExpression(expression, editor);
      return;
    }
    PsiReferenceExpression refExpression = (PsiReferenceExpression)expression;
    JavaResolveResult result = refExpression.advancedResolve(true);
    PsiElement element = result.getElement();

    if (!(element instanceof PsiClass)) {
      super.expandForChooseExpression(expression, editor);
      return;
    }

    WriteAction.run(() -> insertConstructorCallWithSmartBraces(expression, editor, (PsiClass)element));
  }

  public void insertConstructorCallWithSmartBraces(@NotNull PsiElement expression,
                                                   @NotNull Editor editor,
                                                   @NotNull PsiClass javaClass) {
    Document document = editor.getDocument();
    PsiFile file = expression.getContainingFile();
    Project project = expression.getProject();

    SmartPsiElementPointer<PsiClass> pointer = SmartPointerManager.getInstance(editor.getProject()).createSmartPsiElementPointer(javaClass);
    replaceExpressionTextByNewExpressionInDocument(project, expression, document);

    if (!javaClass.isValid()) {
      javaClass = pointer.getElement();
    }

    //noinspection ConstantConditions / false positive
    if (javaClass == null) return;

    JavaPsiClassReferenceElement item = JavaClassNameCompletionContributor.createClassLookupItem(javaClass, true);

    item.handleInsert(createInsertionContext(editor, file, item));
  }

  @NotNull
  private static InsertionContext createInsertionContext(@NotNull Editor editor,
                                                         @NotNull PsiFile file,
                                                         @NotNull JavaPsiClassReferenceElement item) {
    Document document = editor.getDocument();
    final OffsetMap offsetMap = new OffsetMap(document);
    final InsertionContext insertionContext = new InsertionContext(offsetMap,
                                                                   Lookup.AUTO_INSERT_SELECT_CHAR,
                                                                   new LookupElement[]{item},
                                                                   file, editor, false);

    int offset = editor.getCaretModel().getOffset();
    return CompletionUtil.newContext(insertionContext, item, offset, offset);
  }

  private static void replaceExpressionTextByNewExpressionInDocument(@NotNull Project project,
                                                                     @NotNull PsiElement expression,
                                                                     @NotNull Document document) {
    TextRange range = expression.getTextRange();
    document.replaceString(range.getStartOffset(), range.getEndOffset(), "new " + expression.getText());

    PsiDocumentManager.getInstance(project).commitDocument(document);
  }
}
