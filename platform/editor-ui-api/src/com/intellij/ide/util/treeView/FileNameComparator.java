// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.treeView;

import com.intellij.openapi.util.text.NaturalComparator;

import java.util.Comparator;

public class FileNameComparator implements Comparator<String> {

  /**
   * @deprecated use {@link #getInstance()} instead
   */
  @Deprecated
  public static final Comparator<String> INSTANCE = new FileNameComparator();

  public static Comparator<String> getInstance() {
    return INSTANCE;
  }

  @Override
  public int compare(String s1, String s2) {
    return NaturalComparator.naturalCompare(s1, s2, s1.length(), s2.length(), true, true);
  }
}
