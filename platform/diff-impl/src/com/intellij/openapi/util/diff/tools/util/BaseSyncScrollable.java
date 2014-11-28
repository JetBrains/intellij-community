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
package com.intellij.openapi.util.diff.tools.util;

import com.intellij.openapi.util.diff.util.Side;
import org.jetbrains.annotations.NotNull;

public abstract class BaseSyncScrollable implements SyncScrollSupport.SyncScrollable {
  /*
   * should call handler on pairs of lines, that represent boundaries of blocks, that are 'similar'
   *
   * last pair should be (-1, -1)
   *
   * handler will return false if precessing should be aborted
   */
  protected abstract void processHelper(@NotNull ScrollHelper helper);

  public int transfer(@NotNull Side side, int line) {
    ScrollHelper helper = new ScrollHelper(side, line);
    processHelper(helper);

    int master1 = helper.getMaster1();
    int master2 = helper.getMaster2();
    int slave1 = helper.getSlave1();
    int slave2 = helper.getSlave2();

    // TODO: old algorithm could cause less jumping. Use it here?
    if (master1 == -1) return line;
    if (master2 == -1) return line - master1 + slave1;
    if (master1 == master2) return slave1;

    return (slave2 - slave1) / (master2 - master1) * (line - master1) + slave1;
  }

  protected static class ScrollHelper {
    @NotNull private final Side mySide;
    private final int myLine;

    public ScrollHelper(@NotNull Side side, int line) {
      mySide = side;
      myLine = line;
    }

    private int myLeft1 = -1;
    private int myLeft2 = -1;
    private int myRight1 = -1;
    private int myRight2 = -1;

    public boolean process(int left, int right) {
      myLeft1 = myLeft2;
      myRight1 = myRight2;

      myLeft2 = left;
      myRight2 = right;

      return mySide.isLeft() ? myLine >= left : myLine >= right;
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
