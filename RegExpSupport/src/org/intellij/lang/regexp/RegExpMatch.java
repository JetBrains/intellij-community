// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp;

import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * @author Bas Leijdekkers
 */
public class RegExpMatch {

  private final IntArrayList groups = new IntArrayList();

  public void add(int start, int end) {
    groups.add(start);
    groups.add(end);
  }

  /**
   * @return the number of groups (including the 0th group)
   */
  public int count() {
    return groups.size() / 2;
  }

  /**
   * @return the start of the ith group.
   */
  public int start(int i) {
    if (i < 0 || i > count() - 1) throw new IllegalArgumentException();

    return groups.getInt(i * 2);
  }

  /**
   * @return the end of the ith group
   */
  public int end(int i) {
    if (i < 0 || i > count() - 1) throw new IllegalArgumentException();

    return groups.getInt(i * 2 + 1);
  }
}
