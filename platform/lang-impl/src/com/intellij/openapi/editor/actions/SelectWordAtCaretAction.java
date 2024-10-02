// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.actions;

import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class SelectWordAtCaretAction extends EditorAction implements DumbAware {
  public SelectWordAtCaretAction() {
    super(new DefaultHandler());
    setInjectedContext(true);
  }

  @Override
  protected @Nullable Editor getEditor(@NotNull DataContext dataContext) {
    return TextComponentEditorAction.getEditorFromContext(dataContext);
  }

  private static final class DefaultHandler extends EditorActionHandler.ForEachCaret {
    @Override
    public void doExecute(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      Document document = editor.getDocument();

      if (EditorUtil.isPasswordEditor(editor)) {
        caret.setSelection(0, document.getTextLength());
        return;
      }

      int lineNumber = caret.getLogicalPosition().line;
      int caretOffset = caret.getOffset();
      if (lineNumber >= document.getLineCount()) {
        return;
      }

      boolean camel = editor.getSettings().isCamelWords();
      List<TextRange> ranges = new ArrayList<>();

      int textLength = document.getTextLength();
      if (caretOffset == textLength) caretOffset--;
      if (caretOffset < 0) return;

      SelectWordUtil.addWordOrLexemeSelection(camel, editor, caretOffset, ranges);

      // add whole line selection
      int line = document.getLineNumber(caretOffset);
      ranges.add(new TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)));

      final TextRange selectionRange = new TextRange(caret.getSelectionStart(), caret.getSelectionEnd());

      TextRange minimumRange = new TextRange(0, document.getTextLength());
      for (TextRange range : ranges) {
        if (range.contains(selectionRange) && !range.equals(selectionRange)) {
          if (minimumRange.contains(range)) {
            minimumRange = range;
          }
        }
      }

      caret.setSelection(minimumRange.getStartOffset(), minimumRange.getEndOffset());
    }
  }

  public static final class Handler extends EditorActionHandler.ForEachCaret {
    private final EditorActionHandler myDefaultHandler;

    public Handler(EditorActionHandler defaultHandler) {
      myDefaultHandler = defaultHandler;
    }

    @Override
    public void doExecute(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      final IndentGuideDescriptor guide = editor.getIndentsModel().getCaretIndentGuide();
      if (guide != null && !caret.hasSelection() && isWhitespaceAtCaret(caret)) {
        selectWithGuide(caret, guide);
      }
      else {
        myDefaultHandler.execute(editor, caret, dataContext);
      }
    }

    private static boolean isWhitespaceAtCaret(Caret caret) {
      final Document doc = caret.getEditor().getDocument();

      final int offset = caret.getOffset();
      if (offset >= doc.getTextLength()) return false;

      final char c = doc.getCharsSequence().charAt(offset);
      return c == ' ' || c == '\t' || c == '\n';
    }

    private static void selectWithGuide(Caret caret, IndentGuideDescriptor guide) {
      Editor editor = caret.getEditor();
      final Document doc = editor.getDocument();
      int startOffset = editor.logicalPositionToOffset(new LogicalPosition(guide.startLine, 0));
      int endOffset = guide.endLine >= doc.getLineCount() ? doc.getTextLength() : doc.getLineStartOffset(guide.endLine);

      final VirtualFile file = editor.getVirtualFile();
      if (file != null) {
        // Make sure selection contains closing matching brace.

        final CharSequence chars = doc.getCharsSequence();
        int nonWhitespaceOffset = CharArrayUtil.shiftForward(chars, endOffset, " \t\n");
        HighlighterIterator iterator = editor.getHighlighter().createIterator(nonWhitespaceOffset);
        if (BraceMatchingUtil.isRBraceToken(iterator, chars, file.getFileType())) {
          if (editor.offsetToLogicalPosition(iterator.getStart()).column == guide.indentLevel) {
            endOffset = iterator.getEnd();
            endOffset = CharArrayUtil.shiftForward(chars, endOffset, " \t");
            if (endOffset < chars.length() && chars.charAt(endOffset) == '\n') endOffset++;
          }
        }
      }

      caret.setSelection(startOffset, endOffset);
    }
  }
}
