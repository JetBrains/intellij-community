/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler.impl;

import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.StringInterner;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jun 21, 2006
 */
public class InternedPath {
  private final @NotNull List<String> myValue;

  public InternedPath(StringInterner interner, String url, final char separator) {
    myValue = convert(interner, url, separator);
  }

  public String toString() {
    return join(myValue, '/');
  }

  public static List<String> convert(StringInterner interner, String value, char delim) {
    final List<String> result = new ArrayList<String>();
    int start = 0;
    final int len = value.length();
    for (int idx = 0; idx < len; idx++) {
      if (value.charAt(idx) == delim) {
        result.add(interner.intern(value.substring(start, idx)));
        start = idx + 1;
      }
    }
    if (start < value.length()) {
      result.add(interner.intern(value.substring(start)));
    }
    if (len > 0 && value.charAt(len-1) == delim) { // ends with delimiter
      result.add("");
    }
    return result;
  }

  public static String join(List<String> value, char separator) {
    final int size = value.size();
    if (size > 1) {
      final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
        builder.append(value.get(0));
        for (int idx = 1; idx < size; idx++) {
          builder.append(separator).append(value.get(idx));
        }
        return builder.toString();
      }
      finally {
        StringBuilderSpinAllocator.dispose(builder);
      }
    }
    else if (size == 1){
      return value.get(0);
    }
    return "";
  }
}
