// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.util.text.StringHash;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;

public final class UsedColors {
  private static final Key<Object/*UsedColor or UsedColor[]*/> USED_COLOR = Key.create("USED_COLOR");

  public static final AtomicInteger counter = new AtomicInteger();
  private static class UsedColor {
    @NotNull final String name;
    final int index;

    UsedColor(@NotNull String name, int index) {
      this.name = name;
      this.index = index;
      counter.incrementAndGet();
    }
  }

  public static int getOrAddColorIndex(@NotNull final UserDataHolderEx context,
                                       @NotNull final String name,
                                       int colorsCount) {
    int colorIndex;
    while (true) {
      Object data = context.getUserData(USED_COLOR);
      Object newColors;
      if (data == null) {
        colorIndex = hashColor(name, colorsCount);
        newColors = new UsedColor(name, colorIndex); // put an object instead of array to save space
      }
      else if (data instanceof UsedColor) {
        UsedColor usedColor = (UsedColor)data;
        if (usedColor.name.equals(name)) {
          colorIndex = usedColor.index;
          newColors = null; // found, no need to create new
        }
        else {
          int hashedIndex = hashColor(name, colorsCount);
          if (hashedIndex == usedColor.index) hashedIndex = (hashedIndex + 1) % colorsCount;
          colorIndex = hashedIndex;
          UsedColor newColor = new UsedColor(name, colorIndex);
          newColors = new UsedColor[]{usedColor, newColor};
        }
      }
      else {
        colorIndex = -1;
        int hashedIndex = hashColor(name, colorsCount);
        int[] index2usage = new int[colorsCount];
        UsedColor[] usedColors = (UsedColor[])data;
        for (UsedColor usedColor : usedColors) {
          int index = usedColor.index;
          index2usage[index]++;
          if (usedColor.name.equals(name)) {
            colorIndex = index;
            break;
          }
        }
        if (colorIndex == -1) {
          int minIndex1 = indexOfMin(index2usage, hashedIndex, colorsCount);
          int minIndex2 = indexOfMin(index2usage, 0, hashedIndex);
          colorIndex = index2usage[minIndex1] <= index2usage[minIndex2] ? minIndex1 : minIndex2;
          UsedColor newColor = new UsedColor(name, colorIndex);
          newColors = ArrayUtil.append(usedColors, newColor);
        }
        else {
          newColors = null;
        }
      }
      if (newColors == null || context.replace(USED_COLOR, data, newColors)) {
        break;
      }
    }

    return colorIndex;
  }

  private static int hashColor(@NotNull String name, int colorsCount) {
    return Math.abs(StringHash.murmur(name, 0x55AA) % colorsCount);
  }

  @Contract(pure = true)
  private static int indexOfMin(int @NotNull [] values, int start, int end) {
    int min = Integer.MAX_VALUE;
    int minIndex = start;
    for (int i = start; i < end; i++) {
      int value = values[i];
      if (value < min) {
        min = value;
        minIndex = i;
      }
    }
    return minIndex;
  }
}
