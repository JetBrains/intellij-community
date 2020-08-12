// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.treeView;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;

import java.util.Comparator;

public class FileNameComparator implements Comparator<String> {
  public static final Comparator<String> INSTANCE = new FileNameComparator();

  @Override
  public int compare(String s1, String s2) {
    //for super natural comparison (IDEA-80435)
    Pair<String, String> normalized = normalize(s1, s2);
    return StringUtil.naturalCompare(normalized.first, normalized.second);
  }

  private static Pair<String, String> normalize(String s1, String s2) {
    int minLen = Math.min(s1.length(), s2.length());
    StringBuilder sb1 = null;
    StringBuilder sb2 = null;
    for (int i = 0; i < minLen; i++) {
      char ch1 = s1.charAt(i);
      char ch2 = s2.charAt(i);
      boolean needSwap1 = ch1 == '-' && ch2 != '_';
      boolean needSwap2 = ch2 == '-' && ch1 != '_';

      if (needSwap1) {
        if (sb1 == null) sb1 = new StringBuilder(s1);
        sb1.setCharAt(i, '_');
      }
      if (needSwap2) {
        if (sb2 == null) sb2 = new StringBuilder(s2);
        sb2.setCharAt(i, '_');
      }
    }

    if (sb1 != null) s1 = sb1.toString();
    if (sb2 != null) s2 = sb2.toString();
    return Pair.create(s1, s2);
  }
}
