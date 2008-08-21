package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.highlighting.BraceMatcher;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public abstract class BraceMatcherBasedSelectioner extends ExtendWordSelectionHandlerBase {

  public List<TextRange> select(final PsiElement e, final CharSequence editorText, final int cursorOffset, final Editor editor) {
    final VirtualFile file = e.getContainingFile().getVirtualFile();
    final FileType fileType = file == null? null : file.getFileType();
    if (fileType == null) return super.select(e, editorText, cursorOffset, editor);
    final TextRange totalRange = e.getTextRange();
    final HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(totalRange.getStartOffset());
    final BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType);
    if (braceMatcher == null) return super.select(e, editorText, cursorOffset, editor);

    final ArrayList<TextRange> result = new ArrayList<TextRange>();
    final LinkedList<Pair<Integer, IElementType>> stack = new LinkedList<Pair<Integer, IElementType>>();
    while (!iterator.atEnd() && iterator.getStart() < totalRange.getEndOffset()) {
      final Pair<Integer, IElementType> last;
      if (braceMatcher.isLBraceToken(iterator, editorText, fileType)) {
        stack.addLast(Pair.create(iterator.getStart(), iterator.getTokenType()));
      }
      else if (braceMatcher.isRBraceToken(iterator, editorText, fileType)
          && !stack.isEmpty() && braceMatcher.isPairBraces((last = stack.getLast()).second, iterator.getTokenType())) {
        stack.removeLast();
        final int start = last.first;
        result.addAll(expandToWholeLine(editorText, new TextRange(start, iterator.getEnd())));
        int bodyStart = start + 1;
        int bodyEnd = iterator.getEnd() - 1;
        while (Character.isWhitespace(editorText.charAt(bodyStart))) bodyStart ++;
        while (Character.isWhitespace(editorText.charAt(bodyEnd - 1))) bodyEnd --;
        result.addAll(expandToWholeLine(editorText, new TextRange(bodyStart, bodyEnd)));
      }
      iterator.advance();
    }
    result.add(e.getTextRange());
    return result;
  }
}