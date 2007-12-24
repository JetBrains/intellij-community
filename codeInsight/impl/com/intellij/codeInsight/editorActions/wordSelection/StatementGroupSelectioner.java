package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspCodeBlock;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.editor.Editor;

import java.util.List;
import java.util.ArrayList;

public class StatementGroupSelectioner extends BasicSelectioner {
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiStatement || e instanceof PsiComment && !(e instanceof PsiDocComment);
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = new ArrayList<TextRange>();

    PsiElement parent = e.getParent();

    if (!(parent instanceof PsiCodeBlock) && !(parent instanceof PsiBlockStatement) || parent instanceof JspCodeBlock) {
      return result;
    }


    PsiElement startElement = e;
    PsiElement endElement = e;


    while (startElement.getPrevSibling() != null) {
      PsiElement sibling = startElement.getPrevSibling();

      if (sibling instanceof PsiJavaToken) {
        PsiJavaToken token = (PsiJavaToken)sibling;
        if (token.getTokenType() == JavaTokenType.LBRACE) {
          break;
        }
      }

      if (sibling instanceof PsiWhiteSpace) {
        PsiWhiteSpace whiteSpace = (PsiWhiteSpace)sibling;

        String[] strings = LineTokenizer.tokenize(whiteSpace.getText().toCharArray(), false);
        if (strings.length > 2) {
          break;
        }
      }

      startElement = sibling;
    }

    while (startElement instanceof PsiWhiteSpace) {
      startElement = startElement.getNextSibling();
    }

    while (endElement.getNextSibling() != null) {
      PsiElement sibling = endElement.getNextSibling();

      if (sibling instanceof PsiJavaToken) {
        PsiJavaToken token = (PsiJavaToken)sibling;
        if (token.getTokenType() == JavaTokenType.RBRACE) {
          break;
        }
      }

      if (sibling instanceof PsiWhiteSpace) {
        PsiWhiteSpace whiteSpace = (PsiWhiteSpace)sibling;

        String[] strings = LineTokenizer.tokenize(whiteSpace.getText().toCharArray(), false);
        if (strings.length > 2) {
          break;
        }
      }

      endElement = sibling;
    }

    while (endElement instanceof PsiWhiteSpace) {
      endElement = endElement.getPrevSibling();
    }

    result.addAll(expandToWholeLine(editorText, new TextRange(startElement.getTextRange().getStartOffset(),
                                                              endElement.getTextRange().getEndOffset())));

    return result;
  }
}
