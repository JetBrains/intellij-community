package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ChangeNewOperatorTypeFix implements IntentionAction {
  private final PsiType myType;
  private final PsiNewExpression myExpression;

  private ChangeNewOperatorTypeFix(PsiType type, PsiNewExpression expression) {
    myType = type;
    myExpression = expression;
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("change.new.operator.type.text", myExpression.getText(), myType.getPresentableText(), myType instanceof PsiArrayType ? "" : "()");
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("change.new.operator.type.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myType.isValid() && myExpression.isValid() && myExpression.getManager().isInProject(myExpression);
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    changeNewOperatorType(myExpression, myType, editor);
  }

  private static void changeNewOperatorType(PsiExpression originalExpression, PsiType type, final Editor editor) throws IncorrectOperationException {
    PsiNewExpression newExpression;
    PsiElementFactory factory = PsiManager.getInstance(originalExpression.getProject()).getElementFactory();
    int caretOffset;
    TextRange selection;
    if (type instanceof PsiArrayType) {
      caretOffset = -2;
      @NonNls String text = "new " + type.getDeepComponentType().getCanonicalText() + "[0]";
      for (int i = 1; i < type.getArrayDimensions(); i++) {
        text += "[]";
        caretOffset -= 2;
      }

      newExpression = (PsiNewExpression)factory.createExpressionFromText(text, originalExpression);
      selection = new TextRange(caretOffset, caretOffset+1);
    }
    else {
      newExpression = (PsiNewExpression)factory.createExpressionFromText("new " + type.getCanonicalText() + "()", originalExpression);
      selection = null;
      caretOffset = -1;
    }
    PsiElement element = originalExpression.replace(newExpression);
    editor.getCaretModel().moveToOffset(element.getTextRange().getEndOffset() + caretOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    if (selection != null) {
      selection = selection.shiftRight(element.getTextRange().getEndOffset());
      editor.getSelectionModel().setSelection(selection.getStartOffset(), selection.getEndOffset());
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

  public static void register(final HighlightInfo highlightInfo, PsiExpression expression, final PsiType lType) {
    expression = PsiUtil.deparenthesizeExpression(expression);
    if (!(expression instanceof PsiNewExpression)) return;
    PsiNewExpression newExpression = (PsiNewExpression)expression;
    QuickFixAction.registerQuickFixAction(highlightInfo, new ChangeNewOperatorTypeFix(lType, newExpression));
  }
}
