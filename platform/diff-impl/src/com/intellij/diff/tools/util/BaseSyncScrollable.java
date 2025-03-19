// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util;

import com.intellij.diff.util.Range;
import com.intellij.diff.util.Side;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.diagnostic.Logger.getInstance;

public abstract class BaseSyncScrollable implements SyncScrollSupport.SyncScrollable {
  private static final Logger LOG = getInstance(BaseSyncScrollable.class);

  /*
   * should call handler on pairs of lines, that represent boundaries of blocks, that are 'similar'
   * pairs should not form "crossings": x1 <= x2 <=> y1 <= y2
   *
   * first pair should be (0, 0), last pair should be (lineCount1, lineCount2)
   *
   * handler will return false if precessing should be aborted
   */
  protected abstract void processHelper(@NotNull ScrollHelper helper);

  @Override
  public int transfer(@NotNull Side baseSide, int line) {
    Range range = getRange(baseSide, line);
    return transferLine(line, range);
  }

  @Override
  public @NotNull Range getRange(@NotNull Side baseSide, int line) {
    if (line < 0) {
      LOG.error("Invalid line number: " + line);
      return idRange(line);
    }

    ScrollHelper helper = new ScrollHelper(baseSide, line);
    processHelper(helper);

    int master1 = helper.getMaster1();
    int master2 = helper.getMaster2();
    int slave1 = helper.getSlave1();
    int slave2 = helper.getSlave2();
    return new Range(master1, master2, slave1, slave2);
  }

  protected static class ScrollHelper {
    private final @NotNull Side mySide;
    private final int myLine;

    public ScrollHelper(@NotNull Side side, int line) {
      mySide = side;
      myLine = line;
    }

    private int myLeft1 = 0;
    private int myLeft2 = 0;
    private int myRight1 = 0;
    private int myRight2 = 0;

    /*
     * false - stop processing
     */
    public boolean process(int left, int right) {
      myLeft1 = myLeft2;
      myRight1 = myRight2;

      myLeft2 = left;
      myRight2 = right;

      return myLine > mySide.select(left, right);
    }

    public int getMaster1() {
      return mySide.select(myLeft1, myRight1);
    }

    public int getMaster2() {
      return mySide.select(myLeft2, myRight2);
    }

    public int getSlave1() {
      return mySide.select(myRight1, myLeft1);
    }

    public int getSlave2() {
      return mySide.select(myRight2, myLeft2);
    }
  }

  public static int transferLine(int line, @NotNull Range range) {
    if (range.start1 == line) return range.start2;
    if (range.end1 == line) return range.end2;
    if (range.end1 < line) return (line - range.end1) + range.end2;

    return Math.min(range.start2 + (line - range.start1), range.end2);
  }

  public static @NotNull Range idRange(int line) {
    return new Range(line, line + 1, line, line + 1);
  }
}
