// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ex;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.Interval;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class VisibleRangeMerger<T> {
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
    int visibleLinesStart = EditorUtil.yToLogicalLineRange(myEditor, clip.y).intervalStart();
    int visibleLinesEnd = EditorUtil.yToLogicalLineRange(myEditor, clip.y + Math.max(clip.height - 1, 0)).intervalEnd() + 1;

    for (Range range : ranges) {
      int line1 = range.getLine1();
      int line2 = range.getLine2();

      if (line2 < visibleLinesStart) continue;
      if (line1 > visibleLinesEnd) break;

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
    Pair<@NotNull Interval, @Nullable Interval> pair1 = EditorUtil.logicalLineToYRange(myEditor, start);
    int visualStart = pair1.first.intervalStart();

    int sharedPrefixHeight;
    if (pair1.second == null) {
      sharedPrefixHeight = pair1.first.intervalEnd() - pair1.first.intervalStart();
    }
    else {
      sharedPrefixHeight = pair1.second.intervalStart() - pair1.first.intervalStart();
    }

    if (start == end) {
      if (sharedPrefixHeight != 0) {
        appendChange(range, new ChangedLines<>(visualStart, visualStart + sharedPrefixHeight, Range.MODIFIED, flags));
      }
      else {
        appendChange(range, new ChangedLines<>(visualStart, visualStart, type, flags));
      }
    }
    else {
      Pair<@NotNull Interval, @Nullable Interval> pair2 = EditorUtil.logicalLineToYRange(myEditor, end - 1);
      int visualEnd = pair2.first.intervalEnd();

      int sharedSuffixHeight;
      if (pair2.second == null) {
        sharedSuffixHeight = pair2.first.intervalEnd() - pair2.first.intervalStart();
      }
      else {
        sharedSuffixHeight = pair2.first.intervalEnd() - pair2.second.intervalEnd();
      }

      if (type == Range.EQUAL || type == Range.MODIFIED) {
        appendChange(range, new ChangedLines<>(visualStart, visualEnd, type, flags));
      }
      else {
        if (sharedPrefixHeight != 0 && visualEnd - visualStart > sharedPrefixHeight) {
          appendChange(range, new ChangedLines<>(visualStart, visualStart + sharedPrefixHeight, Range.MODIFIED, flags));
          visualStart += sharedPrefixHeight;
          sharedPrefixHeight = 0;
        }
        if (sharedSuffixHeight != 0 && visualEnd - visualStart > sharedSuffixHeight) {
          appendChange(range, new ChangedLines<>(visualStart, visualEnd - sharedSuffixHeight, type, flags));
          appendChange(range, new ChangedLines<>(visualEnd - sharedSuffixHeight, visualEnd, Range.MODIFIED, flags));
        }
        else {
          byte bodyType = sharedPrefixHeight != 0 || sharedSuffixHeight != 0 ? Range.MODIFIED : type;
          appendChange(range, new ChangedLines<>(visualStart, visualEnd, bodyType, flags));
        }
      }
    }
  }

  private void appendChange(@NotNull Range range, @NotNull ChangedLines<T> newChange) {
    ChangedLines<T> lastItem = ContainerUtil.getLastItem(myBlock.changes);
    if (lastItem != null && lastItem.y2 < newChange.y1) {
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

    if (lastChange.y1 == lastChange.y2 &&
        newChange.y1 == newChange.y2) {
      assert lastChange.y1 == newChange.y1;
      byte type = mergeTypes(lastChange, newChange);
      T flags = myFlagsProvider.mergeFlags(lastChange.flags, newChange.flags);
      changes.add(new ChangedLines<>(lastChange.y1, lastChange.y2, type, flags));
    }
    else if (lastChange.y1 == lastChange.y2 && newChange.type == Range.EQUAL ||
             newChange.y1 == newChange.y2 && lastChange.type == Range.EQUAL) {
      changes.add(lastChange);
      changes.add(newChange);
    }
    else if (lastChange.type == newChange.type &&
             Objects.equals(lastChange.flags, newChange.flags)) {
      int union1 = Math.min(lastChange.y1, newChange.y1);
      int union2 = Math.max(lastChange.y2, newChange.y2);
      changes.add(new ChangedLines<>(union1, union2, lastChange.type, lastChange.flags));
    }
    else {
      int intersection1 = Math.max(lastChange.y1, newChange.y1);
      int intersection2 = Math.min(lastChange.y2, newChange.y2);

      if (lastChange.y1 != intersection1) {
        changes.add(new ChangedLines<>(lastChange.y1, intersection1, lastChange.type, lastChange.flags));
      }

      if (intersection1 != intersection2) {
        byte type = mergeTypes(lastChange, newChange);
        T flags = myFlagsProvider.mergeFlags(lastChange.flags, newChange.flags);
        changes.add(new ChangedLines<>(intersection1, intersection2, type, flags));
      }

      if (newChange.y2 != intersection2) {
        changes.add(new ChangedLines<>(intersection2, newChange.y2, newChange.type, newChange.flags));
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


    FlagsProvider<Unit> EMPTY = new FlagsProvider<>() {
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
