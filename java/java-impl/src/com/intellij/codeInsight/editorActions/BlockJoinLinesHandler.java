package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.IncorrectOperationException;

public class BlockJoinLinesHandler implements JoinLinesHandlerDelegate {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.BlockJoinLinesHandler");

  public int tryJoinLines(final Document document, final PsiFile psiFile, final int start, final int end) {
    PsiElement elementAtStartLineEnd = psiFile.findElementAt(start);
    PsiElement elementAtNextLineStart = psiFile.findElementAt(end);
    if (elementAtStartLineEnd == null || elementAtNextLineStart == null) return -1;
    if (!(elementAtStartLineEnd instanceof PsiJavaToken) || ((PsiJavaToken)elementAtStartLineEnd).getTokenType() != JavaTokenType.LBRACE) {
      return -1;
    }
    final PsiElement codeBlock = elementAtStartLineEnd.getParent();
    if (!(codeBlock instanceof PsiCodeBlock)) return -1;
    if (!(codeBlock.getParent() instanceof PsiBlockStatement)) return -1;
    final PsiElement parentStatement = codeBlock.getParent().getParent();

    final CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(elementAtStartLineEnd.getProject());
    if (!(parentStatement instanceof PsiIfStatement && codeStyleSettings.IF_BRACE_FORCE != CodeStyleSettings.FORCE_BRACES_ALWAYS ||
          parentStatement instanceof PsiWhileStatement && codeStyleSettings.WHILE_BRACE_FORCE != CodeStyleSettings.FORCE_BRACES_ALWAYS ||
          (parentStatement instanceof PsiForStatement || parentStatement instanceof PsiForeachStatement) &&
          codeStyleSettings.FOR_BRACE_FORCE != CodeStyleSettings.FORCE_BRACES_ALWAYS ||
                                                                                     parentStatement instanceof PsiDoWhileStatement &&
                                                                                     codeStyleSettings
                                                                                       .DOWHILE_BRACE_FORCE !=
                                                                                                            CodeStyleSettings
                                                                                                              .FORCE_BRACES_ALWAYS)) {
      return -1;
    }
    PsiElement foundStatement = null;
    for (PsiElement element = elementAtStartLineEnd.getNextSibling(); element != null; element = element.getNextSibling()) {
      if (element instanceof PsiWhiteSpace) continue;
      if (element instanceof PsiJavaToken &&
          ((PsiJavaToken)element).getTokenType() == JavaTokenType.RBRACE &&
          element.getParent() == codeBlock) {
        if (foundStatement == null) return -1;
        break;
      }
      if (foundStatement != null) return -1;
      foundStatement = element;
    }
    try {
      final PsiElement newStatement = codeBlock.getParent().replace(foundStatement);

      return newStatement.getTextRange().getStartOffset();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return -1;
  }
}
