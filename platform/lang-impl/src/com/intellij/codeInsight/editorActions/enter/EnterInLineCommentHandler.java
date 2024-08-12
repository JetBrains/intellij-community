// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.editorActions.enter;

import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

public final class EnterInLineCommentHandler extends EnterHandlerDelegateAdapter {
  private static final String WHITESPACE = " \t";

  @Override
  public Result preprocessEnter(final @NotNull PsiFile file,
                                final @NotNull Editor editor,
                                final @NotNull Ref<Integer> caretOffsetRef,
                                final @NotNull Ref<Integer> caretAdvance,
                                final @NotNull DataContext dataContext,
                                final EditorActionHandler originalHandler) {
    CodeDocumentationAwareCommenter commenter = EnterInCommentUtil.getDocumentationAwareCommenter(dataContext);
    if (commenter == null) return Result.Continue;

    int caretOffset = caretOffsetRef.get();
    Pair<Integer, String> lineCommentStart = getLineCommentStart(editor, caretOffset, commenter);
    int lineCommentStartOffset = lineCommentStart.first;
    if (lineCommentStartOffset < 0) return Result.Continue;

    Document document = editor.getDocument();
    CharSequence text = document.getImmutableCharSequence();
    final int offset = CharArrayUtil.shiftForward(text, caretOffset, WHITESPACE);
    if (offset >= document.getTextLength() || text.charAt(offset) == '\n') return Result.Continue;

    String prefix = lineCommentStart.second;
    assert prefix != null : "Line Comment type is set but Line Comment Prefix is null!";
    String prefixTrimmed = prefix.trim();

    int beforeCommentOffset = CharArrayUtil.shiftBackward(text, lineCommentStartOffset - 1, WHITESPACE);
    boolean onlyCommentInCaretLine = beforeCommentOffset < 0 || text.charAt(beforeCommentOffset) == '\n';

    CharSequence spacing = " ";
    if (StringUtil.startsWith(text, offset, prefix)) {
      int afterPrefix = offset + prefixTrimmed.length();
      if (afterPrefix < document.getTextLength() && text.charAt(afterPrefix) != ' ') {
        document.insertString(afterPrefix, spacing);
      }
      caretOffsetRef.set(offset);
    }
    else {
      if (onlyCommentInCaretLine) {
        int indentStart = lineCommentStartOffset + prefix.trim().length();
        int indentEnd = CharArrayUtil.shiftForward(text, indentStart, WHITESPACE);
        CharSequence currentLineSpacing = text.subSequence(indentStart, indentEnd);
        if (TodoConfiguration.getInstance().isMultiLine() &&
            EnterInCommentUtil.isTodoText(text, lineCommentStartOffset, caretOffset) &&
            EnterInCommentUtil.isTodoText(text, lineCommentStartOffset, DocumentUtil.getLineEndOffset(lineCommentStartOffset, document))) {
          spacing = currentLineSpacing + " ";
        }
        else if (!currentLineSpacing.isEmpty()) {
          spacing = currentLineSpacing;
        }
        int textStart = CharArrayUtil.shiftForward(text, caretOffset, WHITESPACE);
        document.deleteString(caretOffset, textStart);
      }
      else {
        if (text.charAt(caretOffset) == ' ') spacing = "";
      }
      document.insertString(caretOffset, prefixTrimmed + spacing);
    }

    if (onlyCommentInCaretLine) {
      caretAdvance.set(prefixTrimmed.length() + spacing.length());
    }
    return Result.DefaultForceIndent;
  }

  private static Pair<Integer, String> getLineCommentStart(@NotNull Editor editor,
                                                           int offset,
                                                           @NotNull CodeDocumentationAwareCommenter commenter) {
    if (offset < 1) return Pair.pair(-1, null);
    EditorHighlighter highlighter = editor.getHighlighter();
    HighlighterIterator iterator = highlighter.createIterator(offset - 1);
    if (!commenter.getLineCommentTokenTypes().contains(iterator.getTokenType())) return Pair.pair(-1, null);

    int startOffset = iterator.getStart();
    String commentedLineText = editor.getDocument().getText(new TextRange(startOffset, offset));
    String possiblePrefix = ContainerUtil.find(commenter.getLineCommentPrefixes(), commentedLineText::contains);
    String prefix = possiblePrefix != null ? possiblePrefix : commenter.getLineCommentPrefix();
    if (startOffset + (prefix == null ? 0 : prefix.length()) <= offset) {
      return Pair.pair(startOffset, prefix);
    }
    return Pair.pair(-1, null);
  }
}
