package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.formatting.service.FormattingServiceUtil;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.LanguageFormatting;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.util.containers.ComparatorUtil.max;
import static com.intellij.util.containers.ComparatorUtil.min;

public class DefaultTypingActionsExtension implements TypingActionsExtension {
  private static final Logger LOG = Logger.getInstance(DefaultTypingActionsExtension.class);
  private static final int LINE_LIMIT_FOR_BULK_CHANGE = 5000;

  @Override
  public void startCopy(@NotNull Project project, @NotNull Editor editor) {
    final List<CopyPastePostProcessor<? extends TextBlockTransferableData>> postProcessors =
      ContainerUtil.filter(CopyPastePostProcessor.EP_NAME.getExtensionList(), p -> p.requiresAllDocumentsToBeCommitted(editor, project));
    final List<CopyPastePreProcessor> preProcessors =
      ContainerUtil.filter(CopyPastePreProcessor.EP_NAME.getExtensionList(), p -> p.requiresAllDocumentsToBeCommitted(editor, project));
    final boolean commitAllDocuments = !preProcessors.isEmpty() || !postProcessors.isEmpty();
    if (LOG.isDebugEnabled()) {
      LOG.debug("CommitAllDocuments: " + commitAllDocuments);
      if (commitAllDocuments) {
        final String processorNames = StringUtil.join(preProcessors, ",") + "," + StringUtil.join(postProcessors, ",");
        LOG.debug("Processors with commitAllDocuments requirement: [" + processorNames + "]");
      }
    }
    if (commitAllDocuments) {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
    }
    else {
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    }
  }

  @Override
  public boolean isSuitableContext(@NotNull Project project, @NotNull Editor editor) {
    LOG.error("Should not be called for `DefaultTypingActionsExtension`. Please, override.");
    return false;
  }

  @Override
  public void format(@NotNull Project project,
                     @NotNull Editor editor,
                     int howtoReformat,
                     int startOffset,
                     int endOffset,
                     int anchorColumn,
                     boolean indentBeforeReformat,
                     boolean formatInjected) {
    if (formatInjected && !(editor instanceof EditorWindow)) {
      PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
      PsiFile psiFile = psiDocumentManager.getPsiFile(editor.getDocument());
      if (psiFile != null) {
        final List<DocumentWindow> injectedDocuments = InjectedLanguageManager.getInstance(project)
          .getCachedInjectedDocumentsInRange(psiFile, new TextRange(startOffset, endOffset));
        for (DocumentWindow injectedDocument : injectedDocuments) {
          PsiFile injectedPsiFile = psiDocumentManager.getPsiFile(injectedDocument);
          if (injectedPsiFile == null || CodeFormatterFacade.shouldDelegateToTopLevel(injectedPsiFile)) {
            continue;
          }
          @SuppressWarnings("deprecation")
          Editor injectedEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(editor, injectedPsiFile);
          int injectedStartOffset = max(0, injectedDocument.hostToInjected(startOffset));
          int injectedEndOffset = min(injectedDocument.getTextLength(), injectedDocument.hostToInjected(endOffset));

          doFormat(project, injectedEditor, howtoReformat, injectedStartOffset, injectedEndOffset, anchorColumn, indentBeforeReformat);
        }
      }
    }

    doFormat(project, editor, howtoReformat, startOffset, endOffset, anchorColumn, indentBeforeReformat);
  }

  private static void checkOffsets(Document document, int startOffset, int endOffset) {
    if (startOffset < 0 || endOffset > document.getTextLength() || endOffset < startOffset) {
      throw new IllegalArgumentException("Invalid offsets: startOffset="+startOffset+"; endOffset="+endOffset+"; document length="+document.getTextLength());
    }
  }

  private void doFormat(@NotNull Project project,
                        @NotNull Editor editor,
                        int howtoReformat,
                        int startOffset,
                        int endOffset,
                        int anchorColumn,
                        boolean indentBeforeReformat) {
    checkOffsets(editor.getDocument(), startOffset, endOffset);

    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());

    switch (howtoReformat) {
      case CodeInsightSettings.INDENT_BLOCK -> indentBlock(project, editor, startOffset, endOffset, anchorColumn);
      case CodeInsightSettings.INDENT_EACH_LINE -> indentEachLine(project, editor, startOffset, endOffset);
      case CodeInsightSettings.REFORMAT_BLOCK -> {
        final RangeMarker bounds = editor.getDocument().createRangeMarker(startOffset, endOffset);
        if (indentBeforeReformat) {
          indentEachLine(project, editor, startOffset, endOffset); // this is needed for example when inserting a comment before method
        }
        reformatRange(project, editor, bounds.getStartOffset(), bounds.getEndOffset());
        bounds.dispose();
      }
    }
  }

  protected void adjustLineIndent(@NotNull Project project, @NotNull Editor editor, int startOffset, int endOffset) {
    Document document = editor.getDocument();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    documentManager.commitDocument(document);
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) return;
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    try {
      codeStyleManager.adjustLineIndent(file, new TextRange(startOffset, endOffset));
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  protected void adjustLineIndent(@NotNull Project project, @NotNull Editor editor, int offset) {
    Document document = editor.getDocument();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    documentManager.commitDocument(document);
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) return;
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    try {
      codeStyleManager.adjustLineIndent(file, offset);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  protected void reformatRange(@NotNull Project project, @NotNull Editor editor, int startOffset, int endOffset) {
    Document document = editor.getDocument();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    documentManager.commitDocument(document);
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) return;
    try {
      FormattingServiceUtil.asyncFormatElement(file, new TextRange(startOffset, endOffset), true);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void indentBlock(@NotNull Project project, @NotNull Editor editor, int startOffset, int endOffset, int originalCaretCol) {
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    final Document document = editor.getDocument();
    PsiFile file = documentManager.getPsiFile(document);
    if (file == null) {
      return;
    }

    if (LanguageFormatting.INSTANCE.forContext(file) != null) {
      indentBlockWithFormatter(project, editor, startOffset, endOffset);
    }
    else {
      indentPlainTextBlock(document, startOffset, endOffset, originalCaretCol);
    }
  }

  private void indentEachLine(@NotNull Project project, @NotNull Editor editor, int startOffset, int endOffset) {
    final CharSequence text = editor.getDocument().getCharsSequence();
    if (startOffset > 0 && endOffset > startOffset + 1 && text.charAt(endOffset - 1) == '\n' && text.charAt(startOffset - 1) == '\n') {
      // There is a possible situation that pasted text ends by a line feed. We don't want to proceed it when a text is
      // pasted at the first line column.
      // Example:
      //    text to paste:
      //'if (true) {
      //'
      //    source:
      // if (true) {
      //     int i = 1;
      //     int j = 1;
      // }
      //
      //
      // We get the following on paste then:
      // if (true) {
      //     if (true) {
      //         int i = 1;
      //     int j = 1;
      // }
      //
      // We don't want line 'int i = 1;' to be indented here.
      endOffset--;
    }
    adjustLineIndent(project, editor, startOffset, endOffset);
  }

  private static void indentPlainTextBlock(final @NotNull Document document, final int startOffset, final int endOffset, final int indentLevel) {
    CharSequence chars = document.getCharsSequence();
    int spaceEnd = CharArrayUtil.shiftForward(chars, startOffset, " \t");
    final int startLine = document.getLineNumber(startOffset);
    if (spaceEnd > endOffset || indentLevel <= 0 || startLine >= document.getLineCount() - 1 || chars.charAt(spaceEnd) == '\n') {
      return;
    }

    int endLine = startLine + 1;
    while (endLine < document.getLineCount() && document.getLineStartOffset(endLine) < endOffset) endLine++;

    final String indentString = StringUtil.repeatSymbol(' ', indentLevel);
    indentLines(document, startLine + 1, endLine - 1, indentString);
  }

  private void indentBlockWithFormatter(@NotNull Project project,  @NotNull Editor editor, int startOffset, int endOffset) {

    // Algorithm: the main idea is to process the first line of the pasted block, adjust its indent if necessary, calculate indent
    // adjustment string and apply to each line of the pasted block starting from the second one.
    //
    // We differentiate the following possible states here:
    //   --- pasted block doesn't start new line, i.e. there are non-white space symbols before it at the first line.
    //      Example:
    //         old content [pasted line 1
    //                pasted line 2]
    //      Indent adjustment string is just the first line indent then.
    //
    //   --- pasted block starts with empty line(s)
    //      Example:
    //         old content [
    //            pasted line 1
    //            pasted line 2]
    //      We parse existing indents of the pasted block then, adjust its first non-blank line via formatter and adjust indent
    //      of subsequent pasted lines in order to preserve old indentation.
    //
    //   --- pasted block is located at the new line and starts with white space symbols.
    //       Example:
    //          [   pasted line 1
    //                 pasted line 2]
    //       We parse existing indents of the pasted block then, adjust its first line via formatter and adjust indent of the pasted lines
    //       starting from the second one in order to preserve old indentation.
    //
    //   --- pasted block is located at the new line but doesn't start with white space symbols.
    //       Example:
    //           [pasted line 1
    //         pasted line 2]
    //       We adjust the first line via formatter then and apply first line's indent to all subsequent pasted lines.

    final Document document = editor.getDocument();
    final CharSequence chars = document.getCharsSequence();
    final int firstLine = document.getLineNumber(startOffset);
    final int firstLineStart = document.getLineStartOffset(firstLine);

    // There is a possible case that we paste block that ends with new line that is empty or contains only white space symbols.
    // We want to preserve indent for the original document line where paste was performed.
    // Example:
    //   Original:
    //       if (test) {
    //   <caret>    }
    //
    //   Pasting: 'int i = 1;\n'
    //   Expected:
    //       if (test) {
    //           int i = 1;
    //       }
    boolean saveLastLineIndent = false;
    for (int i = endOffset - 1; i >= startOffset; i--) {
      final char c = chars.charAt(i);
      if (c == '\n') {
        saveLastLineIndent = true;
        break;
      }
      if (c != ' ' && c != '\t') {
        break;
      }
    }

    final int lastLine;
    if (saveLastLineIndent) {
      lastLine = document.getLineNumber(endOffset) - 1;
      // Remove white space symbols at the pasted text if any.
      int start = document.getLineStartOffset(lastLine + 1);
      if (start < endOffset) {
        int i = CharArrayUtil.shiftForward(chars, start, " \t");
        if (i > start) {
          i = Math.min(i, endOffset);
          document.deleteString(start, i);
        }
      }

      // Insert white space from the start line of the pasted block.
      int indentToKeepEndOffset = Math.min(startOffset, CharArrayUtil.shiftForward(chars, firstLineStart, " \t"));
      if (indentToKeepEndOffset > firstLineStart) {
        document.insertString(start, chars.subSequence(firstLineStart, indentToKeepEndOffset));
      }
    }
    else {
      lastLine = document.getLineNumber(endOffset);
    }

    final int i = CharArrayUtil.shiftBackward(chars, startOffset - 1, " \t");

    // Handle a situation when pasted block doesn't start a new line.
    if (chars.charAt(startOffset) != '\n' && i > 0 && chars.charAt(i) != '\n') {
      int firstNonWsOffset = CharArrayUtil.shiftForward(chars, firstLineStart, " \t");
      if (firstNonWsOffset > firstLineStart) {
        CharSequence toInsert = chars.subSequence(firstLineStart, firstNonWsOffset);
        indentLines(document, firstLine + 1, lastLine, toInsert);
      }
      return;
    }

    final int j = CharArrayUtil.shiftForward(chars, startOffset, " \t\n");
    if (j >= endOffset) {
      // Pasted text contains white space/line feed symbols only, do nothing.
      return;
    }

    final int anchorLine = document.getLineNumber(j);
    final int anchorLineStart = document.getLineStartOffset(anchorLine);
    adjustLineIndent(project, editor, j);

    // Handle situation when pasted block starts with non-white space symbols.
    if (anchorLine == firstLine && j == startOffset) {
      int indentOffset = CharArrayUtil.shiftForward(chars, firstLineStart, " \t");
      if (indentOffset > firstLineStart) {
        CharSequence toInsert = chars.subSequence(firstLineStart, indentOffset);
        indentLines(document, firstLine + 1, lastLine, toInsert);
      }
      return;
    }

    // Handle situation when pasted block starts from white space symbols. Assume that the pasted text started at the line start,
    // i.e. correct indentation level is stored at the blocks structure.
    final int firstNonWsOffset = CharArrayUtil.shiftForward(chars, anchorLineStart, " \t");
    final int diff = firstNonWsOffset - j;
    if (diff == 0) {
      return;
    }
    if (diff > 0) {
      CharSequence toInsert = chars.subSequence(anchorLineStart, anchorLineStart + diff);
      indentLines(document, anchorLine + 1, lastLine, toInsert);
      return;
    }

    // We've pasted text to the non-first column and exact white space between the line start and caret position on the moment of paste
    // has been removed by formatter during 'adjust line indent'
    // Example:
    //       copied text:
    //                 '   line1
    //                       line2'
    //       after paste:
    //          line start -> '   I   line1
    //                              line2' (I - caret position during 'paste')
    //       formatter removed white space between the line start and caret position, so, current document state is:
    //                        '   line1
    //                              line2'
    if (anchorLine == firstLine && -diff == startOffset - firstLineStart) {
      return;
    }
    if (anchorLine != firstLine || -diff > startOffset - firstLineStart) {
      final int desiredSymbolsToRemove;
      if (anchorLine == firstLine) {
        desiredSymbolsToRemove = -diff - (startOffset - firstLineStart);
      }
      else {
        desiredSymbolsToRemove = -diff;
      }

      Runnable unindentTask = () -> {
        for (int line = anchorLine + 1; line <= lastLine; line++) {
          int currentLineStart = document.getLineStartOffset(line);
          int currentLineIndentOffset = CharArrayUtil.shiftForward(chars, currentLineStart, " \t");
          int symbolsToRemove = Math.min(currentLineIndentOffset - currentLineStart, desiredSymbolsToRemove);
          if (symbolsToRemove > 0) {
            document.deleteString(currentLineStart, currentLineStart + symbolsToRemove);
          }
        }
      };
      DocumentUtil.executeInBulk(document, lastLine - anchorLine > LINE_LIMIT_FOR_BULK_CHANGE, unindentTask);
    }
    else {
      CharSequence toInsert = chars.subSequence(anchorLineStart, diff + startOffset);
      indentLines(document, anchorLine + 1, lastLine, toInsert);
    }
  }

  /**
   * Inserts specified string at the beginning of lines from {@code startLine} to {@code endLine} inclusive.
   */
  private static void indentLines(final @NotNull Document document,
                                  final int startLine, final int endLine, final @NotNull CharSequence indentString) {
    Runnable indentTask = () -> {
      for (int line = startLine; line <= endLine; line++) {
        int lineStartOffset = document.getLineStartOffset(line);
        document.insertString(lineStartOffset, indentString);
      }
    };
    DocumentUtil.executeInBulk(document, endLine - startLine > LINE_LIMIT_FOR_BULK_CHANGE, indentTask);
  }
}
