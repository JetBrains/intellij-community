// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.impl.InvokeTemplateAction;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ConstructionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;

public final class IterateOverIterableIntention implements IntentionAction {
  private final @Nullable PsiExpression myExpression;
  private @IntentionName String myText;

  public IterateOverIterableIntention() {
    this(null);
  }

  public IterateOverIterableIntention(@Nullable PsiExpression expression) {
    myExpression = expression;
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new IterateOverIterableIntention(PsiTreeUtil.findSameElementInCopy(myExpression, target));
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    int offset = editor.getCaretModel().getOffset();
    int startOffset = offset;
    if (editor.getSelectionModel().hasSelection()) {
      final int selStart = editor.getSelectionModel().getSelectionStart();
      final int selEnd = editor.getSelectionModel().getSelectionEnd();
      startOffset = (offset == selStart) ? selEnd : selStart;
    }
    PsiElement element = psiFile.findElementAt(startOffset);
    while (element instanceof PsiWhiteSpace) {
      element = element.getPrevSibling();
    }
    PsiStatement psiStatement = PsiTreeUtil.getParentOfType(element, PsiStatement.class, false);
    if (myExpression == null && psiStatement instanceof PsiExpressionStatement &&
        startOffset == offset && startOffset == psiStatement.getTextRange().getEndOffset() &&
        psiStatement.getLastChild() instanceof PsiErrorElement) {
      // At this position, we provide a quick-fix for error
      return false;
    }
    if (psiStatement != null) {
      startOffset = psiStatement.getTextRange().getStartOffset();
    }
    PsiExpression expression = getIterableExpression(editor, psiFile);
    if (expression == null) return false;
    if (ConstructionUtils.isEmptyCollectionInitializer(expression)) {
      // Empty collection: iterating doesn't make much sense
      return false;
    }
    if (expression instanceof PsiNewExpression && ((PsiNewExpression)expression).getArrayDimensions().length > 0) {
      // new array without initializers: all elements are 0/null/false, so iterating doesn't make much sense
      return false;
    }

    TemplateImpl template = psiFile instanceof PsiJavaFile ? getTemplate() : null;
    if (template == null || template.isDeactivated()) return false;

    // we are checking Template applicability later, because it requires initialization of all (for all languages) template context types
    if (!TemplateManagerImpl.isApplicable(psiFile, offset, template) &&
        !TemplateManagerImpl.isApplicable(psiFile, startOffset, template)) {
      return false;
    }

    myText = JavaBundle.message("intention.name.iterate.over", Objects.requireNonNull(expression.getType()).getPresentableText());
    return true;
  }

  private static @Nullable TemplateImpl getTemplate() {
    return TemplateSettings.getInstance().getTemplate("I", "Java");
  }

  @Override
  public @NotNull String getText() {
    return myText == null ? getFamilyName() : myText;
  }

  private @Nullable PsiExpression getIterableExpression(Editor editor, PsiFile psiFile) {
    if (myExpression != null) {
      if (!myExpression.isValid()) return null;
      final PsiType type = myExpression.getType();
      if (type instanceof PsiArrayType || InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_ITERABLE)) return myExpression;
      return null;
    }
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      PsiElement elementAtStart = psiFile.findElementAt(selectionModel.getSelectionStart());
      PsiElement elementAtEnd = psiFile.findElementAt(selectionModel.getSelectionEnd() - 1);
      if (elementAtStart == null || elementAtStart instanceof PsiWhiteSpace || elementAtStart instanceof PsiComment) {
        elementAtStart = PsiTreeUtil.skipWhitespacesAndCommentsForward(elementAtStart);
        if (elementAtStart == null) return null;
      }
      if (elementAtEnd == null || elementAtEnd instanceof PsiWhiteSpace || elementAtEnd instanceof PsiComment) {
        elementAtEnd = PsiTreeUtil.skipWhitespacesAndCommentsBackward(elementAtEnd);
        if (elementAtEnd == null) return null;
      }
      PsiElement parent = PsiTreeUtil.findCommonParent(elementAtStart, elementAtEnd);
      if (parent instanceof PsiExpression) {
        final PsiType type = ((PsiExpression)parent).getType();
        return type instanceof PsiArrayType || InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_ITERABLE)
               ? (PsiExpression)parent
               : null;
      }
      return null;
    }

    PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
    while (element instanceof PsiWhiteSpace) {
      element = element.getPrevSibling();
    }
    if (element instanceof PsiExpressionStatement) {
      element = ((PsiExpressionStatement)element).getExpression().getLastChild();
    }
    while ((element = PsiTreeUtil.getParentOfType(element, PsiExpression.class, true)) != null) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiMethodCallExpression) continue;
      if (!(parent instanceof PsiExpressionStatement)) return null;
      final PsiType type = ((PsiExpression)element).getType();
      if (type instanceof PsiArrayType || InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_ITERABLE)) return (PsiExpression)element;
    }
    return null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    final TemplateImpl template = getTemplate();
    SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      final PsiExpression iterableExpression = getIterableExpression(editor, psiFile);
      Logger.getInstance(IterateOverIterableIntention.class).assertTrue(iterableExpression != null);

      PsiElement element = PsiTreeUtil.skipSiblingsForward(iterableExpression, PsiWhiteSpace.class, PsiComment.class);
      if (PsiUtil.isJavaToken(element, JavaTokenType.SEMICOLON)) {
        element.delete();
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(psiFile.getViewProvider().getDocument());
      }
      TextRange textRange = iterableExpression.getTextRange();
      selectionModel.setSelection(textRange.getStartOffset(), textRange.getEndOffset());
    }
    new InvokeTemplateAction(template, editor, project, new HashSet<>()).performInCommand();
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("iterate.iterable");
  }
}
