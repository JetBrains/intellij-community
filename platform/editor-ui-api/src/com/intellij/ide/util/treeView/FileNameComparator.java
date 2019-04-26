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
    final int minLen = Math.min(s1.length(), s2.length());
    final StringBuilder sb1 = new StringBuilder(s1);
    final StringBuilder sb2 = new StringBuilder(s2);
    for (int i = 0; i < minLen; i++) {
      final char ch1 = s1.charAt(i);
      final char ch2 = sb2.charAt(i);
      if (ch1 == ch2 && ch1 == '-') {
        sb1.setCharAt(i, '_');
        sb2.setCharAt(i, '_');
      }
      else if (ch1 == '-' && ch2 != '_') {
        sb1.setCharAt(i, '_');
      }
      else if (ch2 == '-' && ch1 != '_') {
        sb2.setCharAt(i, '_');
      }
    }

    s1 = sb1.toString();
    s2 = sb2.toString();
    return Pair.create(s1, s2);
  }
}
