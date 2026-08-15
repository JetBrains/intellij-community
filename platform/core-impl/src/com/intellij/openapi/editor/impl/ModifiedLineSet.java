// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.DocumentText;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.MergingCharSequence;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Arrays;

/**
 * Minimal counterpart to {@link LineSet} for {@link DocumentModStateImpl}: tracks only the per-line
 * "modified" bit, not line offsets.
 * <p/>
 * {@link LineSet}'s own modification-tracking methods ({@link LineSet#isModified}, {@link LineSet#setModified},
 * {@link LineSet#clearModificationFlags}, {@link LineSet#getLineCount}) never read its offsets ({@code myStarts})
 * -- only {@link LineSet#update} needs them, to convert an edit's offsets into line indices for the *old*, pre-edit
 * text. Since {@link DocumentModStateImpl#withPatch} is already handed that old text as a {@link DocumentText}, this
 * class delegates those specific lookups to it ({@link DocumentText#lineNumber}, {@link DocumentText#lineStartOffset})
 * instead of keeping a second copy of the offsets: the {@link DocumentText}'s own {@code LineSet} already computed
 * them for real offset mapping, so re-deriving them here would be exactly the duplicated work this class exists to
 * avoid. This is safe because a {@link com.intellij.openapi.editor.ex.DocumentModState} and its paired
 * {@link DocumentText} are always fed the identical sequence of patches (see {@link DocumentSnapshotImpl#withPatch}),
 * so the old text's line structure is always exactly what this instance's own state was built against.
 * <p/>
 * Per-line separator length ({@link LineSet}'s {@code SEPARATOR_MASK}) isn't tracked at all: every call site of
 * {@link LineSet}'s {@code hasEol}/{@code isLastEmptyLine} only ever inspects the separator of the *last* line, so a
 * single {@link #myLastLineHasSeparator} flag replaces what {@link LineSet} encodes per line.
 * <p/>
 * <b>Immutable and safe for racy publication</b>, same contract as {@link LineSet}: all fields are {@code final} and
 * the backing array is never mutated after construction -- every "mutator" returns a fresh instance. This is relied
 * upon by {@link DocumentModStateImpl}, which caches an instance in a non-volatile field.
 * <p/>
 * Public only so {@code ModifiedLineSetTest} (in the {@code intellij.platform.tests} module) can compare it
 * against {@link LineSet} directly -- same treatment as {@link LineSet#update}'s own {@link VisibleForTesting}.
 * The only real caller remains {@link DocumentModStateImpl}, in the same package and module.
 */
@ApiStatus.Internal
public final class ModifiedLineSet {
  private final boolean @NotNull [] myModified; // one entry per real (tokenized) line; length == real line count
  private final boolean myLastLineHasSeparator; // does the last real line end with a separator?
                                                 // (<=> a synthetic trailing empty line exists, mirroring LineSet.isLastEmptyLine)

  private ModifiedLineSet(boolean @NotNull [] modified, boolean lastLineHasSeparator) {
    myModified = modified;
    myLastLineHasSeparator = lastLineHasSeparator;
  }

  @Contract("_ -> new")
  public static @NotNull ModifiedLineSet create(@NotNull CharSequence text) {
    return create(text, false);
  }

  /**
   * Returns a modified-line set corresponding to the text after the replacement, with all lines touched by the
   * change marked as modified. Callers implementing "whole text replaced" semantics should call
   * {@link #clearModificationFlags} on the result.
   *
   * @param oldText the text this instance's state was built against, *before* the replacement described by
   *                {@code start}/{@code end}/{@code replacement} is applied
   */
  @VisibleForTesting
  public @NotNull ModifiedLineSet update(@NotNull DocumentText oldText, int start, int end, @NotNull CharSequence replacement) {
    if (oldText.length() == 0) {
      return create(replacement, true);
    }

    CharSequence prevText = oldText.chars();
    // if we're breaking or creating a '\r\n' pair, expand the changed range to include it fully
    CharSequence newText = StringUtil.replaceSubSequence(prevText, start, end, replacement);
    if (hasChar(prevText, start - 1, '\r') &&
        (hasChar(prevText, start, '\n') || hasChar(newText, start, '\n'))) {
      replacement = new MergingCharSequence("\r", replacement);
      start--;
    }

    if (hasChar(prevText, end, '\n') &&
        (hasChar(prevText, end - 1, '\r') || hasChar(newText, start + replacement.length() - 1, '\r'))) {
      replacement = new MergingCharSequence(replacement, "\n");
      end++;
    }

    return isSingleLineChange(oldText, start, end, replacement)
           ? updateInsideOneLine(oldText.lineNumber(start))
           : genericUpdate(oldText, start, end, replacement);
  }

  @VisibleForTesting
  public boolean isModified(int index) {
    checkLineIndex(index);
    return !isLastEmptyLine(index) && myModified[index];
  }

  @VisibleForTesting
  public @NotNull ModifiedLineSet setModified(@NotNull IntList indices) {
    if (indices.isEmpty()) {
      return this;
    }
    if (indices.size() == 1) {
      int index = indices.getInt(0);
      if (isLastEmptyLine(index) || isModified(index)) return this;
    }

    boolean[] modified = myModified.clone();
    for (int i = 0; i < indices.size(); i++) {
      modified[indices.getInt(i)] = true;
    }
    return new ModifiedLineSet(modified, myLastLineHasSeparator);
  }

  /**
   * @param endLine is exclusive, {@code Integer.MAX_VALUE} means the last line
   */
  @VisibleForTesting
  public @NotNull ModifiedLineSet clearModificationFlags(int startLine, int endLine) {
    if (startLine > endLine) {
      throw new IllegalArgumentException("endLine < startLine: " + endLine + " < " + startLine + "; lineCount: " + getLineCount());
    }
    int lineCount = getLineCount();
    if (lineCount == 0 && startLine == 0) {
      if (endLine == 0 || endLine == Integer.MAX_VALUE) {
        // 0 and MAX_VALUE are special values, allow them bypassing checkLineIndex
        return this;
      }
    }
    checkLineIndex(startLine);
    if (endLine == Integer.MAX_VALUE) {
      endLine = lineCount;
    }
    checkLineIndex(endLine - 1);

    if (isLastEmptyLine(endLine - 1)) endLine--;
    if (startLine >= endLine) return this;

    boolean[] modified = myModified.clone();
    Arrays.fill(modified, startLine, endLine, false);
    return new ModifiedLineSet(modified, myLastLineHasSeparator);
  }

  @VisibleForTesting
  public int getLineCount() {
    return myModified.length + (myLastLineHasSeparator ? 1 : 0);
  }

  private boolean isSingleLineChange(@NotNull DocumentText oldText, int start, int end, @NotNull CharSequence replacement) {
    if (start == 0 && end == oldText.length() && replacement.length() == 0) return false;

    int startLine = oldText.lineNumber(start);
    return startLine == oldText.lineNumber(end) && !CharArrayUtil.containLineBreaks(replacement) && !isLastEmptyLine(startLine);
  }

  private @NotNull ModifiedLineSet updateInsideOneLine(int line) {
    boolean[] modified = myModified.clone();
    modified[line] = true;
    return new ModifiedLineSet(modified, myLastLineHasSeparator);
  }

  @Contract("_, _, _, _ -> new")
  private @NotNull ModifiedLineSet genericUpdate(@NotNull DocumentText oldText, int startOffset, int endOffset, @NotNull CharSequence replacement) {
    int startLine = oldText.lineNumber(startOffset);
    int endLine = oldText.lineNumber(endOffset);

    Shape patch = tokenize(replacement);

    int startLineStart = oldText.lineStartOffset(startLine);
    int oldLength = oldText.length();
    boolean addStartLine = startOffset - startLineStart > 0 || patch.lineCount > 0 || endOffset < oldLength;
    boolean addEndLine = endOffset < oldLength && replacement.length() > 0 && patch.lastLineHasSeparator;
    int newLineCount = startLine + (addStartLine ? 1 : 0) +
                        Math.max(patch.lineCount - 1, 0) +
                        (addEndLine ? 1 : 0) + Math.max(myModified.length - endLine - 1, 0);

    boolean[] modified = new boolean[newLineCount];

    if (startLine > 0) {
      System.arraycopy(myModified, 0, modified, 0, startLine);
    }

    int toIndex = startLine;
    if (addStartLine) {
      modified[toIndex] = true;
      toIndex++;
    }

    int interiorCount = Math.max(patch.lineCount - 1, 0);
    Arrays.fill(modified, toIndex, toIndex + interiorCount, true);
    toIndex += interiorCount;

    if (endOffset < oldLength) {
      if (addEndLine) {
        modified[toIndex] = true;
        toIndex++;
      }
      // toIndex > 0 always holds here (same as the analogous branch in LineSet.genericUpdate): reaching this
      // block already required endOffset < oldLength, which alone forces addStartLine true (it is one of
      // addStartLine's OR'd conditions), so toIndex is already >= startLine + 1 >= 1 by this point.
      else if (toIndex > 0) {
        modified[toIndex - 1] = true;
      }
    }

    int tailCount = myModified.length - (endLine + 1);
    if (tailCount > 0) {
      System.arraycopy(myModified, endLine + 1, modified, toIndex, tailCount);
    }

    boolean newLastLineHasSeparator;
    if (endOffset < oldLength) {
      // the tail survives verbatim, so whatever the text ends with is unchanged
      newLastLineHasSeparator = myLastLineHasSeparator;
    }
    else if (replacement.length() > 0) {
      // nothing of the old text survives past startOffset; the new end is the (possibly \r\n-adjusted) replacement's own end
      newLastLineHasSeparator = patch.lastLineHasSeparator;
    }
    else {
      // pure deletion through the end: the new end is whatever was just before startOffset. The \r\n-adjustment
      // above already guarantees startOffset never lands inside a separator, so this is true exactly when
      // startOffset sits precisely at a (non-first) line boundary -- the preceding line's separator is what's left.
      newLastLineHasSeparator = startOffset == startLineStart && startLine > 0;
    }

    return new ModifiedLineSet(modified, newLastLineHasSeparator);
  }

  private boolean isLastEmptyLine(int index) {
    return index == myModified.length && myLastLineHasSeparator;
  }

  private void checkLineIndex(int index) {
    if (index < 0 || index >= getLineCount()) {
      throw new IndexOutOfBoundsException("Wrong line: " + index + ". Available lines count: " + getLineCount());
    }
  }

  private static @NotNull ModifiedLineSet create(@NotNull CharSequence text, boolean markModified) {
    Shape shape = tokenize(text);
    boolean[] modified = new boolean[shape.lineCount];
    if (markModified) {
      Arrays.fill(modified, true);
    }
    return new ModifiedLineSet(modified, shape.lastLineHasSeparator);
  }

  private static boolean hasChar(CharSequence s, int index, char c) {
    return index >= 0 && index < s.length() && s.charAt(index) == c;
  }

  /** The shape of a tokenized text that matters for modification tracking: how many lines, and does the last one have a separator. */
  private static @NotNull Shape tokenize(@NotNull CharSequence text) {
    int lineCount = 0;
    boolean lastLineHasSeparator = false;
    LineTokenizer tokenizer = new LineTokenizer(text);
    while (!tokenizer.atEnd()) {
      lineCount++;
      lastLineHasSeparator = tokenizer.getLineSeparatorLength() > 0;
      tokenizer.advance();
    }
    return new Shape(lineCount, lastLineHasSeparator);
  }

  private static final class Shape {
    final int lineCount;
    final boolean lastLineHasSeparator;

    Shape(int lineCount, boolean lastLineHasSeparator) {
      this.lineCount = lineCount;
      this.lastLineHasSeparator = lastLineHasSeparator;
    }
  }
}
