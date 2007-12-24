package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;

public class StringLiteralPasteProcessor implements PastePreProcessor {
  public String preprocess(final Project project, final PsiFile file, final Editor editor, String text, final RawText rawText) {
    final Document document = editor.getDocument();
    PsiDocumentManager.getInstance(project).commitDocument(document);
    int caretOffset = editor.getCaretModel().getOffset();
    PsiElement elementAtCaret = file.findElementAt(caretOffset);
    if (elementAtCaret instanceof PsiJavaToken && caretOffset > elementAtCaret.getTextOffset()) {
      final IElementType tokenType = ((PsiJavaToken)elementAtCaret).getTokenType();
      if (tokenType == JavaTokenType.STRING_LITERAL) {
        if (rawText != null && rawText.rawText != null) return rawText.rawText; // Copied from the string literal. Copy as is.

        StringBuilder buffer = new StringBuilder(text.length());
        CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(project);
        @NonNls String breaker = codeStyleSettings.BINARY_OPERATION_SIGN_ON_NEXT_LINE ? "\\n\"\n+ \"" : "\\n\" +\n\"";
        final String[] lines = LineTokenizer.tokenize(text.toCharArray(), false, true);
        for (int i = 0; i < lines.length; i++) {
          String line = lines[i];
          buffer.append(StringUtil.escapeStringCharacters(line));
          if (i != lines.length - 1) buffer.append(breaker);
        }
        text = buffer.toString();
      }
      else if (tokenType == JavaTokenType.CHARACTER_LITERAL) {
        if (rawText != null && rawText.rawText != null) return rawText.rawText; // Copied from the string literal. Copy as is.
      }
    }
    return text;
  }
}
