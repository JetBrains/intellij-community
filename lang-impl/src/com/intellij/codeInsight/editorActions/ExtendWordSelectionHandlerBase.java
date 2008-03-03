package com.intellij.codeInsight.editorActions;

import com.intellij.psi.PsiElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.text.CharArrayUtil;

import java.util.List;
import java.util.ArrayList;

/**
 * @author yole
 */
public abstract class ExtendWordSelectionHandlerBase implements ExtendWordSelectionHandler {
  public abstract boolean canSelect(PsiElement e);

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {

    final TextRange originalRange = e.getTextRange();
    List<TextRange> ranges = expandToWholeLine(editorText, originalRange, true);

    if (ranges.size() == 1 && ranges.contains(originalRange)) {
      ranges = expandToWholeLine(editorText, originalRange, false);
    }

    List<TextRange> result = new ArrayList<TextRange>();
    result.addAll(ranges);
    return result;
  }

  public static List<TextRange> expandToWholeLine(CharSequence text, TextRange range, boolean isSymmetric) {
    int textLength = text.length();
    List<TextRange> result = new ArrayList<TextRange>();

    if (range == null) {
      return result;
    }

    boolean hasNewLines = false;

    for (int i = range.getStartOffset(); i < range.getEndOffset(); i++) {
      char c = text.charAt(i);

      if (c == '\r' || c == '\n') {
        hasNewLines = true;
        break;
      }
    }

    if (!hasNewLines) {
      result.add(range);
    }


    int startOffset = range.getStartOffset();
    int endOffset = range.getEndOffset();
    int index1 = CharArrayUtil.shiftBackward(text, startOffset - 1, " \t");
    if (endOffset > startOffset && text.charAt(endOffset - 1) == '\n' || text.charAt(endOffset - 1) == '\r') {
      endOffset--;
    }
    int index2 = Math.min(textLength, CharArrayUtil.shiftForward(text, endOffset, " \t"));

    if (index1 < 0
        || text.charAt(index1) == '\n'
        || text.charAt(index1) == '\r'
        || index2 == textLength
        || text.charAt(index2) == '\n'
        || text.charAt(index2) == '\r') {

      if (!isSymmetric) {
        if (index1 < 0 || text.charAt(index1) == '\n' || text.charAt(index1) == '\r') {
          startOffset = index1 + 1;
        }

        if (index2 == textLength || text.charAt(index2) == '\n' || text.charAt(index2) == '\r') {
          endOffset = index2;
          if (endOffset < textLength) {
            endOffset++;
            if (endOffset < textLength && text.charAt(endOffset - 1) == '\r' && text.charAt(endOffset) == '\n') {
              endOffset++;
            }
          }
        }

        result.add(new TextRange(startOffset, endOffset));
      }
      else {
        if ((index1 < 0 || text.charAt(index1) == '\n' || text.charAt(index1) == '\r') &&
            (index2 == textLength || text.charAt(index2) == '\n' || text.charAt(index2) == '\r')) {
          startOffset = index1 + 1;
          endOffset = index2;
          if (endOffset < textLength) {
            endOffset++;
            if (endOffset < textLength && text.charAt(endOffset - 1) == '\r' && text.charAt(endOffset) == '\n') {
              endOffset++;
            }
          }
          result.add(new TextRange(startOffset, endOffset));
        }
        else {
          result.add(range);
        }
      }
    }
    else {
      result.add(range);
    }

    return result;
  }

  public static List<TextRange> expandToWholeLine(CharSequence text, TextRange range) {
    return expandToWholeLine(text, range, true);
  }

  protected List<TextRange> extendSelectionToWord(final PsiElement e, final CharSequence editorText, final int cursorOffset, final Editor editor) {
    List<TextRange> ranges;
    if (canSelect(e)) {
      ranges = select(e, editorText, cursorOffset, editor);
    }
    else {
      ranges = new ArrayList<TextRange>();
    }
    SelectWordUtil.addWordSelection(editor.getSettings().isCamelWords(), editorText, cursorOffset, ranges);
    return ranges;
  }
}