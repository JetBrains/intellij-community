package com.intellij.database.run.ui.grid;

public final class GridModelUpdaterUtil {
  public static int[] getColumnsIndicesRange(int first, int length) {
    int[] range = new int[length];
    for (int i = 0; i < length; i++) {
      range[i] = first + i;
    }
    return range;
  }
}
