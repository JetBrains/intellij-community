// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.ide.PasteProvider;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;

/**
 * Disable all preprocessing to get the string AS IS
 */
public class RawStringLiteralPasteProcessor implements PasteProvider {

  @Override
  public void performPaste(@NotNull DataContext dataContext) {
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
    if (editor == null || file == null) return;

    String text = getTextInClipboard();
    if (text == null) return;

    final Document document = editor.getDocument();
    PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);

    final int offset = editor.getCaretModel().getOffset();
    PsiElement stringLiteral = findRawStringLiteralAtCaret(file, offset);
    if (stringLiteral == null) return;

    int leadingTicsSequence = PsiRawStringLiteralUtil.getLeadingTicksSequence(text);
    int trailingTicsSequence = PsiRawStringLiteralUtil.getTrailingTicksSequence(text);

    String prefix = "";
    TextRange textRange = stringLiteral.getTextRange();
    if (offset == textRange.getStartOffset() + 1 && leadingTicsSequence > 0) {
      prefix = "\"" + StringUtil.repeat("`", leadingTicsSequence) + "\" + ";
      text = text.substring(leadingTicsSequence);
    }

    String suffix = "";
    if (offset == textRange.getEndOffset() - 1 && trailingTicsSequence > 0) {
      suffix = "+ \"" + StringUtil.repeat("`", trailingTicsSequence) + "\"";
      text = text.substring(0, text.length() - trailingTicsSequence);
    }

    String literalText = stringLiteral.getText();
    int ticsLength = PsiRawStringLiteralUtil.getLeadingTicksSequence(literalText);
    String quotes = literalText.substring(0, ticsLength);
    String additionalQuotes = PsiRawStringLiteralUtil.getAdditionalTicks(text, quotes);
    insertAtCaret(StringUtil.convertLineSeparators(text), additionalQuotes, stringLiteral, editor, prefix, suffix);
  }

  @Override
  public boolean isPastePossible(@NotNull DataContext dataContext) {
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);

    return editor != null &&
           file != null &&
           findRawStringLiteralAtCaret(file, editor.getCaretModel().getOffset()) != null &&
           getTextInClipboard() != null;
  }

  @Override
  public boolean isPasteEnabled(@NotNull DataContext dataContext) {
    return isPastePossible(dataContext);
  }

  @Nullable
  private static String getTextInClipboard() {
    return CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
  }

  @Nullable
  private static PsiElement findRawStringLiteralAtCaret(PsiFile file, int offset) {
    final PsiElement elementAtSelectionStart = file.findElementAt(offset);
    if (elementAtSelectionStart == null) {
      return null;
    }
    ASTNode node = elementAtSelectionStart.getNode();
    return node != null && node.getElementType() == JavaTokenType.RAW_STRING_LITERAL ? elementAtSelectionStart : null;
  }


  private static void insertAtCaret(final String text,
                                    final String additionalQuotes,
                                    final PsiElement element,
                                    final Editor editor, String prefix, String suffix) {
    final Project project = editor.getProject();
    if (project == null) return;

    RangeMarker range = editor.getDocument().createRangeMarker(element.getTextRange());
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);

    final PsiFile file = documentManager.getPsiFile(editor.getDocument());
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;

    WriteCommandAction.runWriteCommandAction(project, "Raw paste", null, () -> {
      Document document = editor.getDocument();
      documentManager.commitDocument(document);

      EditorModificationUtil.insertStringAtCaret(editor, text);

      if (!additionalQuotes.isEmpty() || !prefix.isEmpty() || !suffix.isEmpty()) {
        document.insertString(range.getStartOffset(), prefix + additionalQuotes);
        document.insertString(range.getEndOffset(), additionalQuotes + suffix);
      }
    });
  }
}
