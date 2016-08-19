/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 14, 2002
 * Time: 7:40:40 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SelectWordAtCaretAction extends TextComponentEditorAction implements DumbAware {
  public SelectWordAtCaretAction() {
    super(new DefaultHandler());
    setInjectedContext(true);
  }

  private static class DefaultHandler extends EditorActionHandler {
    private DefaultHandler() {
      super(true);
    }

    @Override
    public void doExecute(Editor editor, @Nullable Caret caret, DataContext dataContext) {
      SelectionModel selectionModel = editor.getSelectionModel();
      Document document = editor.getDocument();

      if (EditorUtil.isPasswordEditor(editor)) {
        selectionModel.setSelection(0, document.getTextLength());
        return;
      }

      int lineNumber = editor.getCaretModel().getLogicalPosition().line;
      int caretOffset = editor.getCaretModel().getOffset();
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

      if (ranges.isEmpty()) return;

      final TextRange selectionRange = new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());

      TextRange minimumRange = new TextRange(0, document.getTextLength());
      for (TextRange range : ranges) {
        if (range.contains(selectionRange) && !range.equals(selectionRange)) {
          if (minimumRange.contains(range)) {
            minimumRange = range;
          }
        }
      }

      selectionModel.setSelection(minimumRange.getStartOffset(), minimumRange.getEndOffset());
    }
  }

  public static class Handler extends EditorActionHandler {
    private final EditorActionHandler myDefaultHandler;

    public Handler(EditorActionHandler defaultHandler) {
      super(true);
      myDefaultHandler = defaultHandler;

    }

    @Override
    public void doExecute(Editor editor, @Nullable Caret caret, DataContext dataContext) {
      final IndentGuideDescriptor guide = editor.getIndentsModel().getCaretIndentGuide();
      final SelectionModel selectionModel = editor.getSelectionModel();
      if (guide != null && !selectionModel.hasSelection() && isWhitespaceAtCaret(editor)) {
        selectWithGuide(editor, guide);
      }
      else {
        myDefaultHandler.execute(editor, caret, dataContext);
      }
    }

    private static boolean isWhitespaceAtCaret(Editor editor) {
      final Document doc = editor.getDocument();

      final int offset = editor.getCaretModel().getOffset();
      if (offset >= doc.getTextLength()) return false;

      final char c = doc.getCharsSequence().charAt(offset);
      return c == ' ' || c == '\t' || c == '\n';
    }

    private static void selectWithGuide(Editor editor, IndentGuideDescriptor guide) {
      final Document doc = editor.getDocument();
      int startOffset = editor.logicalPositionToOffset(new LogicalPosition(guide.startLine, 0));
      int endOffset = guide.endLine >= doc.getLineCount() ? doc.getTextLength() : doc.getLineStartOffset(guide.endLine);

      final VirtualFile file = ((EditorEx)editor).getVirtualFile();
      if (file != null) {
        // Make sure selection contains closing matching brace.

        final CharSequence chars = doc.getCharsSequence();
        int nonWhitespaceOffset = CharArrayUtil.shiftForward(chars, endOffset, " \t\n");
        HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(nonWhitespaceOffset);
        if (BraceMatchingUtil.isRBraceToken(iterator, chars, file.getFileType())) {
          if (editor.offsetToLogicalPosition(iterator.getStart()).column == guide.indentLevel) {
            endOffset = iterator.getEnd();
            endOffset = CharArrayUtil.shiftForward(chars, endOffset, " \t");
            if (endOffset < chars.length() && chars.charAt(endOffset) == '\n') endOffset++;
          }
        }
      }

      editor.getSelectionModel().setSelection(startOffset, endOffset);
    }
  }
}
