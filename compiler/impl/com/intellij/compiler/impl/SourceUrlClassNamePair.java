/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler.impl;

import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.StringInterner;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jun 21, 2006
 */
public class SourceUrlClassNamePair {
  private final String[] mySourceUrl;
  private final @Nullable String[] myClassName;

  public SourceUrlClassNamePair(StringInterner interner, String url, @Nullable String className) {
    mySourceUrl = convert(interner, url, '/');
    myClassName = (className != null) ? convert(interner, className, '.') : null;
  }

  public String getSourceUrl() {
    return join(mySourceUrl, '/');
  }

  public @Nullable String getClassName() {
    return (myClassName != null) ? join(myClassName, '.') : null;
  }

  private static String[] convert(StringInterner interner, String value, char delim) {
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

  private static String join(String[] value, char separator) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      if (value.length > 1) {
        builder.append(value[0]);
        for (int idx = 1; idx < value.length; idx++) {
          builder.append(separator).append(value[idx]);
        }
      }
      else {
        for (String s : value) {
          builder.append(s);
        }
      }
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }
}
