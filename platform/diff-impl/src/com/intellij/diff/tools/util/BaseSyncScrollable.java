/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.tools.util;

import com.intellij.diff.util.Side;
import org.jetbrains.annotations.NotNull;

public abstract class BaseSyncScrollable implements SyncScrollSupport.SyncScrollable {
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
    ScrollHelper helper = new ScrollHelper(baseSide, line);
    processHelper(helper);

    int master1 = helper.getMaster1();
    int master2 = helper.getMaster2();
    int slave1 = helper.getSlave1();
    int slave2 = helper.getSlave2();

    if (master1 == line) return slave1;
    if (master2 == line) return slave2;
    if (master2 < line) return (line - master2) + slave2;

    assert master1 != master2;

    return Math.min(slave1 + (line - master1), slave2); // old
    //return (line - master1) * (slave2 - slave1) / (master2 - master1) + slave1; // new
  }

  protected static class ScrollHelper {
    @NotNull private final Side mySide;
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
}
