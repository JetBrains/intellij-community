// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ex;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.intellij.diff.util.DiffDrawUtil.yToLine;
import static com.intellij.diff.util.DiffUtil.getLineCount;

public class VisibleRangeMerger<T> {
  private static final Logger LOG = Logger.getInstance(VisibleRangeMerger.class);

  @NotNull private final Editor myEditor;
  @NotNull private final FlagsProvider<T> myFlagsProvider;

  @NotNull private ChangesBlock<T> myBlock = new ChangesBlock<>();

  @NotNull private final List<ChangesBlock<T>> myResult = new ArrayList<>();

  private VisibleRangeMerger(@NotNull Editor editor, @NotNull FlagsProvider<T> flagsProvider) {
    myEditor = editor;
    myFlagsProvider = flagsProvider;
  }

  public static List<ChangesBlock<Unit>> merge(@NotNull Editor editor,
                                               @NotNull List<? extends Range> ranges,
                                               @NotNull Rectangle clip) {
    return new VisibleRangeMerger<>(editor, FlagsProvider.EMPTY).run(ranges, clip);
  }

  public static <T> List<ChangesBlock<T>> merge(@NotNull Editor editor,
                                                @NotNull List<? extends Range> ranges,
                                                @NotNull FlagsProvider<T> flagsProvider,
                                                @NotNull Rectangle clip) {
    return new VisibleRangeMerger<>(editor, flagsProvider).run(ranges, clip);
  }

  @NotNull
  private List<ChangesBlock<T>> run(@NotNull List<? extends Range> ranges, @NotNull Rectangle clip) {
    int visibleLineStart = yToLine(myEditor, clip.y);
    int visibleLineEnd = yToLine(myEditor, clip.y + clip.height) + 1;

    for (Range range : ranges) {
      int line1 = range.getLine1();
      int line2 = range.getLine2();

      if (line2 < visibleLineStart) continue;
      if (line1 > visibleLineEnd) break;

      T flags = myFlagsProvider.getFlags(range);
      List<Range.InnerRange> innerRanges = range.getInnerRanges();

      if (innerRanges == null || myFlagsProvider.shouldIgnoreInnerRanges(flags)) {
        processLine(range, line1, line2, range.getType(), flags);
      }
      else {
        for (Range.InnerRange innerRange : innerRanges) {
          int innerLine1 = line1 + innerRange.getLine1();
          int innerLine2 = line1 + innerRange.getLine2();
          byte innerType = innerRange.getType();

          processLine(range, innerLine1, innerLine2, innerType, flags);
        }
      }
    }

    finishBlock();
    return myResult;
  }

  private void processLine(@NotNull Range range, int start, int end, byte type, @NotNull T flags) {
    EditorImpl editorImpl = (EditorImpl)myEditor;
    Document document = myEditor.getDocument();
    int lineCount = getLineCount(document);

    int visualStart;
    boolean startHasFolding;
    if (start < lineCount) {
      int startOffset = document.getLineStartOffset(start);
      visualStart = editorImpl.offsetToVisualLine(startOffset);
      startHasFolding = startOffset > 0 && myEditor.getFoldingModel().isOffsetCollapsed(startOffset - 1);
    }
    else {
      LOG.assertTrue(start == lineCount);
      int lastVisualLine = editorImpl.offsetToVisualLine(document.getTextLength());
      visualStart = lastVisualLine + start - lineCount + 1;
      startHasFolding = false;
    }

    if (start == end) {
      if (startHasFolding) {
        appendChange(range, new ChangedLines<>(visualStart, visualStart + 1, Range.MODIFIED, flags));
      }
      else {
        appendChange(range, new ChangedLines<>(visualStart, visualStart, type, flags));
      }
    }
    else {
      int visualEnd;
      boolean endHasFolding;
      if (end < lineCount) {
        int endOffset = document.getLineEndOffset(end - 1);
        visualEnd = editorImpl.offsetToVisualLine(endOffset) + 1;
        endHasFolding = myEditor.getFoldingModel().isOffsetCollapsed(endOffset);
      }
      else {
        LOG.assertTrue(end == lineCount);
        int lastVisualLine = editorImpl.offsetToVisualLine(document.getTextLength());
        visualEnd = lastVisualLine + end - lineCount + 1;
        endHasFolding = false;
      }

      if (type == Range.EQUAL || type == Range.MODIFIED) {
        appendChange(range, new ChangedLines<>(visualStart, visualEnd, type, flags));
      }
      else {
        if (startHasFolding && visualEnd - visualStart > 1) {
          appendChange(range, new ChangedLines<>(visualStart, visualStart + 1, Range.MODIFIED, flags));
          startHasFolding = false;
          visualStart++;
        }
        if (endHasFolding && visualEnd - visualStart > 1) {
          appendChange(range, new ChangedLines<>(visualStart, visualEnd - 1, type, flags));
          appendChange(range, new ChangedLines<>(visualEnd - 1, visualEnd, Range.MODIFIED, flags));
        }
        else {
          byte bodyType = startHasFolding || endHasFolding ? Range.MODIFIED : type;
          appendChange(range, new ChangedLines<>(visualStart, visualEnd, bodyType, flags));
        }
      }
    }
  }

  private void appendChange(@NotNull Range range, @NotNull ChangedLines<T> newChange) {
    ChangedLines<T> lastItem = ContainerUtil.getLastItem(myBlock.changes);
    if (lastItem != null && lastItem.line2 < newChange.line1) {
      finishBlock();
    }

    List<ChangedLines<T>> changes = myBlock.changes;
    List<Range> ranges = myBlock.ranges;

    if (ContainerUtil.getLastItem(ranges) != range) {
      ranges.add(range);
    }

    if (changes.isEmpty()) {
      changes.add(newChange);
      return;
    }

    ChangedLines<T> lastChange = changes.remove(changes.size() - 1);

    if (lastChange.line1 == lastChange.line2 &&
        newChange.line1 == newChange.line2) {
      assert lastChange.line1 == newChange.line1;
      byte type = mergeTypes(lastChange, newChange);
      T flags = myFlagsProvider.mergeFlags(lastChange.flags, newChange.flags);
      changes.add(new ChangedLines<>(lastChange.line1, lastChange.line2, type, flags));
    }
    else if (lastChange.line1 == lastChange.line2 && newChange.type == Range.EQUAL ||
             newChange.line1 == newChange.line2 && lastChange.type == Range.EQUAL) {
      changes.add(lastChange);
      changes.add(newChange);
    }
    else if (lastChange.type == newChange.type &&
             Objects.equals(lastChange.flags, newChange.flags)) {
      int union1 = Math.min(lastChange.line1, newChange.line1);
      int union2 = Math.max(lastChange.line2, newChange.line2);
      changes.add(new ChangedLines<>(union1, union2, lastChange.type, lastChange.flags));
    }
    else {
      int intersection1 = Math.max(lastChange.line1, newChange.line1);
      int intersection2 = Math.min(lastChange.line2, newChange.line2);

      if (lastChange.line1 != intersection1) {
        changes.add(new ChangedLines<>(lastChange.line1, intersection1, lastChange.type, lastChange.flags));
      }

      if (intersection1 != intersection2) {
        byte type = mergeTypes(lastChange, newChange);
        T flags = myFlagsProvider.mergeFlags(lastChange.flags, newChange.flags);
        changes.add(new ChangedLines<>(intersection1, intersection2, type, flags));
      }

      if (newChange.line2 != intersection2) {
        changes.add(new ChangedLines<>(intersection2, newChange.line2, newChange.type, newChange.flags));
      }
    }
  }

  private void finishBlock() {
    if (myBlock.changes.isEmpty()) return;
    myResult.add(myBlock);
    myBlock = new ChangesBlock<>();
  }

  private byte mergeTypes(@NotNull ChangedLines<T> change1, @NotNull ChangedLines<T> change2) {
    return change1.type == change2.type ? change1.type : Range.MODIFIED;
  }

  public interface FlagsProvider<T> {
    @NotNull
    T getFlags(@NotNull Range range);

    @NotNull
    T mergeFlags(@NotNull T flags1, @NotNull T flags2);

    default boolean shouldIgnoreInnerRanges(@NotNull T flag) {
      return false;
    }


    FlagsProvider<Unit> EMPTY = new FlagsProvider<Unit>() {
      @Override
      public @NotNull Unit getFlags(@NotNull Range range) {
        return Unit.INSTANCE;
      }

      @Override
      public @NotNull Unit mergeFlags(@NotNull Unit flags1, @NotNull Unit flags2) {
        return Unit.INSTANCE;
      }

      @Override
      public boolean shouldIgnoreInnerRanges(@NotNull Unit flag) {
        return true;
      }
    };
  }
}
