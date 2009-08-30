package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.psi.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;

import java.util.List;
import java.util.ArrayList;

public class CaseStatementsSelectioner extends BasicSelectioner {
    public boolean canSelect(PsiElement e) {
      return  e.getParent() instanceof PsiCodeBlock &&
             e.getParent().getParent() instanceof PsiSwitchStatement;
    }

    public List<TextRange> select(PsiElement statement, CharSequence editorText, int cursorOffset, Editor editor) {
      List<TextRange> result = new ArrayList<TextRange>();
      PsiElement caseStart = statement;
      PsiElement caseEnd = statement;

      if (statement instanceof PsiSwitchLabelStatement ||
          statement instanceof PsiSwitchStatement) {
        return result;
      }

      PsiElement sibling = statement.getPrevSibling();
      while(sibling != null && !(sibling instanceof PsiSwitchLabelStatement)) {
        if (!(sibling instanceof PsiWhiteSpace)) caseStart = sibling;
        sibling = sibling.getPrevSibling();
      }

      sibling = statement.getNextSibling();
      while(sibling != null && !(sibling instanceof PsiSwitchLabelStatement)) {
        if (!(sibling instanceof PsiWhiteSpace) &&
            !(sibling instanceof PsiJavaToken) // end of switch
           ) {
          caseEnd = sibling;
        }
        sibling = sibling.getNextSibling();
      }

      final Document document = editor.getDocument();
      final int startOffset = document.getLineStartOffset(document.getLineNumber(caseStart.getTextOffset()));
      final int endOffset = document.getLineEndOffset(document.getLineNumber(caseEnd.getTextOffset() + caseEnd.getTextLength())) + 1;

      result.add(new TextRange(startOffset,endOffset));
      return result;
    }
  }
