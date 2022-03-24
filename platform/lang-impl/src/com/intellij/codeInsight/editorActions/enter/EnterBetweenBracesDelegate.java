// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions.enter;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 *  The class is responsible for recognizing the input context of the Enter key (new line) to support indentation.
 *  The indentation procedure in the context can be delegated to a language-specific implementation.
 *  The procedure can skip parsing during typing only if the language-specific implementation is inherited
 *  from <code>{@link EnterBetweenBracesNoCommitDelegate}</code>.
 */
public class EnterBetweenBracesDelegate {
  private static final Logger LOG = Logger.getInstance(EnterBetweenBracesDelegate.class);
  static final LanguageExtension<EnterBetweenBracesDelegate> EP_NAME = new LanguageExtension<>("com.intellij.enterBetweenBracesDelegate");

  /**
   * Checks that the braces belong to the same syntax element, and whether there is a need to calculate indentation or it can be simplified.
   * Usually the implementation checks if both braces are within the same string literal or comment.
   *
   * @param file         The PSI file associated with the document.
   * @param editor       The editor.
   * @param lBraceOffset The left brace offset.
   * @param rBraceOffset The right brace offset.
   * @return <code>true</code> if the left and the right braces are within the same syntax element.
   */
  public boolean bracesAreInTheSameElement(@NotNull PsiFile file, @NotNull Editor editor, int lBraceOffset, int rBraceOffset) {
    PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());
    if (file.findElementAt(lBraceOffset) == file.findElementAt(rBraceOffset)) {
      return true;
    }
    return false;
  }

  /**
   * Reformats the line at the specified offset in the specified file, modifying only the line indent
   * and leaving all other whitespace intact. At the time of call, the document is in the uncommitted state.
   *
   * @param file   The PSI file to reformat.
   * @param offset The offset the line at which should be reformatted.
   */
  protected void formatAtOffset(@NotNull PsiFile file, @NotNull Editor editor, int offset, @Nullable Language language) {
    PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());
    try {
      CodeStyleManager.getInstance(file.getProject()).adjustLineIndent(file, offset);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  /**
   * Detects if the offset in the file is within the comment.
   * Indentation inside the comment is delegated to the standard procedure.
   *
   * @param file   The PSI file associated with the document.
   * @param editor The editor.
   * @param offset The position in the editor.
   * @return <code>true</code> if you need to use the standard indentation procedure in comments.
   */
  public boolean isInComment(@NotNull PsiFile file, @NotNull Editor editor,  int offset) {
    return PsiTreeUtil.getParentOfType(file.findElementAt(offset), PsiComment.class) != null;
  }

  /**
   * @param lBrace The left brace offset.
   * @param rBrace The right brace offset.
   * @return <code>true</code>, if braces are pair for handling.
   */
  protected boolean isBracePair(char lBrace, char rBrace) {
    return (lBrace == '(' && rBrace == ')') || (lBrace == '{' && rBrace == '}');
  }
}
