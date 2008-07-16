package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.lang.ASTNode;

/**
 * @author maxim
 */
public class XmlTagFixer implements Fixer {
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof XmlTag) {
      final ASTNode emptyTagEnd = XmlChildRole.EMPTY_TAG_END_FINDER.findChild(psiElement.getNode());
      final ASTNode endTagEnd = XmlChildRole.START_TAG_END_FINDER.findChild(psiElement.getNode());
      if (emptyTagEnd != null || endTagEnd != null) return;

      int insertionOffset = psiElement.getTextRange().getEndOffset();
      Document doc = editor.getDocument();
      final int caretAt = editor.getCaretModel().getOffset();
      final CharSequence text = doc.getCharsSequence();
      final int probableCommaOffset = CharArrayUtil.shiftForward(text, insertionOffset, " \t");

      //if (caretAt < probableCommaOffset) {
      //  final CharSequence tagName = text.subSequence(psiElement.getTextRange().getStartOffset() + 1, caretAt);
      //  doc.insertString(caretAt, ">");
      //  doc.insertString(probableCommaOffset + 1, "</" +  tagName + ">");
      //  return;
      //}

      char ch;
      if (probableCommaOffset >= text.length() ||
          ( (ch = text.charAt(probableCommaOffset)) != '/' &&
            ch != '>'
          )
         ) {
        doc.insertString(insertionOffset, "/>");
      }
    }
  }
}