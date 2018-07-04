// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspCodeBlock;
import com.intellij.psi.javadoc.PsiDocComment;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class StatementGroupSelectioner extends BasicSelectioner {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof PsiStatement || e instanceof PsiComment && !(e instanceof PsiDocComment);
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = new ArrayList<>();

    PsiElement parent = e.getParent();

    if (!(parent instanceof PsiCodeBlock) && !(parent instanceof PsiBlockStatement) || parent instanceof JspCodeBlock ||
        e instanceof PsiSwitchLabelStatement) {
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

      if (sibling instanceof PsiSwitchLabelStatement) break;
      
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

      if (sibling instanceof PsiSwitchLabelStatement) break;

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
