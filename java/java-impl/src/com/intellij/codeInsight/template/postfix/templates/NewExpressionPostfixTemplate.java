// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.selectorAllExpressionsWithCurrentOffset;

public class NewExpressionPostfixTemplate extends StringBasedPostfixTemplate implements DumbAware {
  private static final Condition<PsiElement> CONSTRUCTOR = expression -> {
    PsiReferenceExpression ref = expression instanceof PsiMethodCallExpression call ? call.getMethodExpression() :
                                 expression instanceof PsiReferenceExpression r ? r :
                                 null;
    if (ref == null) return false;

    PsiExpression qualifier = ref.getQualifierExpression();

    return DumbService.getInstance(ref.getProject()).computeWithAlternativeResolveEnabled(() -> {
      JavaResolveResult result = ref.advancedResolve(true);
      PsiElement element = result.getElement();

      //todo implement proper support for Foo<Bar>, Foo.new Bar()
      if (qualifier != null && (!(qualifier instanceof PsiReferenceExpression) || element == null)) return false;

      if (element == null) return true;
      if (!(element instanceof PsiClass cls)) return false;
      PsiMethod[] constructors = cls.getConstructors();
      if (constructors.length == 0) return true;
      PsiResolveHelper helper = JavaPsiFacade.getInstance(element.getProject()).getResolveHelper();
      // Check whether there's at least one accessible constructor
      return !ContainerUtil.and(constructors, m -> !helper.isAccessible(m, ref, cls));
    });
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
    if (expression instanceof PsiReferenceExpression ref) {
      JavaResolveResult result = ref.advancedResolve(true);
      PsiElement element = result.getElement();

      if (element instanceof PsiClass psiClass) {
        WriteAction.run(() -> insertConstructorCallWithSmartBraces(expression, editor, psiClass));
        return;
      }
    }
    super.expandForChooseExpression(expression, editor);
  }

  public void insertConstructorCallWithSmartBraces(@NotNull PsiElement expression,
                                                   @NotNull Editor editor,
                                                   @NotNull PsiClass javaClass) {
    Document document = editor.getDocument();
    PsiFile file = expression.getContainingFile();
    Project project = expression.getProject();

    SmartPsiElementPointer<PsiClass> pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(javaClass);
    int startOffset = replaceExpressionTextByNewExpressionInDocument(project, expression, document);

    if (!javaClass.isValid()) {
      javaClass = pointer.getElement();
    }

    if (javaClass == null) return;

    JavaPsiClassReferenceElement item = JavaClassNameCompletionContributor.createClassLookupItem(javaClass, true);

    item.handleInsert(createInsertionContext(editor, file, item, startOffset));
  }

  @NotNull
  private static InsertionContext createInsertionContext(@NotNull Editor editor,
                                                         @NotNull PsiFile file,
                                                         @NotNull JavaPsiClassReferenceElement item,
                                                         int startOffset) {
    Document document = editor.getDocument();
    final OffsetMap offsetMap = new OffsetMap(document);
    final InsertionContext insertionContext = new InsertionContext(offsetMap,
                                                                   Lookup.AUTO_INSERT_SELECT_CHAR,
                                                                   new LookupElement[]{item},
                                                                   file, editor, false);

    int offset = editor.getCaretModel().getOffset();
    return CompletionUtil.newContext(insertionContext, item, startOffset, offset);
  }

  private static int replaceExpressionTextByNewExpressionInDocument(@NotNull Project project,
                                                                    @NotNull PsiElement expression,
                                                                    @NotNull Document document) {
    TextRange range = expression.getTextRange();
    String newPrefix = "new ";
    document.replaceString(range.getStartOffset(), range.getEndOffset(), newPrefix + expression.getText());

    PsiDocumentManager.getInstance(project).commitDocument(document);
    return range.getStartOffset() + newPrefix.length();
  }
}
