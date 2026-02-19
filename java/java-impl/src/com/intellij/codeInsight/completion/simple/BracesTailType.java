// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.completion.simple;

import com.intellij.codeInsight.ModNavigatorTailType;
import com.intellij.codeInsight.editorActions.EnterHandler;
import com.intellij.java.syntax.JavaSyntaxBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ModNavigator;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.text.CharArrayUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

public class BracesTailType extends ModNavigatorTailType {

  @Override
  public int processTail(@NotNull ModNavigator editor, int tailOffset) {
    int startOffset = tailOffset;

    CharSequence seq = editor.getDocument().getCharsSequence();
    int nextNonWs = CharArrayUtil.shiftForward(seq, tailOffset, " \t");
    if (nextNonWs < seq.length() && seq.charAt(nextNonWs) == '{') {
      tailOffset = nextNonWs + 1;
    } else {
      tailOffset = insertChar(editor, startOffset, '{');
    }

    PsiDocumentManager manager = PsiDocumentManager.getInstance(editor.getProject());
    Document document = editor.getDocument();
    manager.commitDocument(document);
    PsiFile psiFile = editor.getPsiFile();
    editor.moveCaretTo(tailOffset);
    CodeStyleManager styleManager = CodeStyleManager.getInstance(editor.getProject());
    styleManager.reformatText(psiFile, startOffset, tailOffset);
    int offset = editor.getCaretOffset();
    PsiElement element = PsiTreeUtil.skipWhitespacesBackward(psiFile.findElementAt(offset));
    if (PsiUtil.isJavaToken(element, JavaTokenType.LBRACE)) {
      if (StreamEx.iterate(element, e -> e.getParent()).takeWhile(e -> e != null && !(e instanceof PsiFile))
        .anyMatch(e -> e.getNextSibling() instanceof PsiErrorElement errorElement &&
                       errorElement.getErrorDescription().equals(JavaSyntaxBundle.message("expected.rbrace")))) {
        manager.doPostponedOperationsAndUnblockDocument(document);
        document.insertString(offset, "\n\n}");
        manager.commitDocument(document);
        editor.moveCaretTo(offset + 1);
        styleManager.reformatText(psiFile, offset, offset + 2);
        offset = editor.getCaretOffset();
        String newIndent = styleManager.getLineIndent(document, offset);
        if (newIndent != null) {
          int adjusted = EnterHandler.adjustLineIndentNoCommit(document, offset, newIndent);
          editor.moveCaretTo(adjusted);
        }
      }
    }
    return editor.getCaretOffset();
  }
}