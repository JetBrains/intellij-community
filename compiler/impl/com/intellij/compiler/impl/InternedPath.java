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
  private final @NotNull String[] myValue;

  public InternedPath(StringInterner interner, String url, final char separator) {
    myValue = convert(interner, url, separator);
  }

  public String toString() {
    return join(myValue, '/');
  }

  public static String[] convert(StringInterner interner, String value, char delim) {
    final List<String> result = new ArrayList<String>(10);
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
    return result.toArray(new String[result.size()]);
  }

  public static String join(String[] value, char separator) {
    if (value.length > 1) {
      final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
        builder.append(value[0]);
        for (int idx = 1; idx < value.length; idx++) {
          builder.append(separator).append(value[idx]);
        }
        return builder.toString();
      }
      finally {
        StringBuilderSpinAllocator.dispose(builder);
      }
    }
    else if (value.length == 1){
      return value[0];
    }
    return "";
  }
}
