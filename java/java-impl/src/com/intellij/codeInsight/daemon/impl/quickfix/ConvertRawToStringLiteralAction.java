// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.editorActions.StringLiteralCopyPasteProcessor;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class ConvertRawToStringLiteralAction implements IntentionAction {
  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("convert.to.string.text");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Convert raw to String literal";
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    return PsiUtil.isJavaToken(element, JavaTokenType.RAW_STRING_LITERAL);
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element != null && PsiUtil.isJavaToken(element, JavaTokenType.RAW_STRING_LITERAL)) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiLiteralExpressionImpl) {
        String text = ((PsiLiteralExpressionImpl)parent).getRawString();
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
        PsiElement literalToken = elementFactory.createExpressionFromText("\"\"", file).getFirstChild();
        String preprocessedValue = new StringLiteralCopyPasteProcessor().escapeAndSplit(text, literalToken);
        CodeStyleManager.getInstance(project).reformat(
          parent.replace(elementFactory.createExpressionFromText('\"' + preprocessedValue + '\"', null)));
      }
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
