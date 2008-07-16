package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 3:35:49 PM
 * To change this template use Options | File Templates.
 */
public class EnumFieldFixer implements Fixer {
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiEnumConstant) {
      int insertionOffset = psiElement.getTextRange().getEndOffset();
      Document doc = editor.getDocument();
      final CharSequence text = doc.getCharsSequence();
      final int probableCommaOffset = CharArrayUtil.shiftForward(text, insertionOffset, " \t");
      if (probableCommaOffset >= text.length() || text.charAt(probableCommaOffset) != ',') {
        doc.insertString(insertionOffset, ",");
      }
    }
  }
}
