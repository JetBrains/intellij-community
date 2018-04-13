// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.editorActions.RawStringLiteralPasteProcessor;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.concatenation.CopyConcatenatedStringToClipboardIntention;
import org.jetbrains.annotations.NotNull;

public class ConvertStringLiteralToRawAction implements IntentionAction, LowPriorityAction {
  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("convert.to.raw.string.text");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Convert to raw string literal";
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    return PsiUtil.isJavaToken(element, JavaTokenType.STRING_LITERAL) && 
           PsiUtil.getLanguageLevel(file) == LanguageLevel.JDK_11_PREVIEW;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element != null && PsiUtil.isJavaToken(element, JavaTokenType.STRING_LITERAL)) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiLiteralExpressionImpl) {
        PsiElement elementToReplace = parent;
        String text;
        PsiElement gParent = PsiUtil.skipParenthesizedExprUp(parent.getParent());
        if (gParent instanceof PsiPolyadicExpression) {
          text = CopyConcatenatedStringToClipboardIntention.buildConcatenationText((PsiPolyadicExpression)gParent);
          elementToReplace = gParent;
        }
        else {
          String innerText = ((PsiLiteralExpressionImpl)parent).getInnerText();
          if (innerText == null) return;
          text = StringUtil.unescapeStringCharacters(innerText);
        }
        String additionalQuotes = RawStringLiteralPasteProcessor.getAdditionalQuotes(text, "`");
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
        CodeStyleManager.getInstance(project).reformat(
          elementToReplace.replace(elementFactory.createExpressionFromText('`' + additionalQuotes + text + additionalQuotes + '`', null)));
      }
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
