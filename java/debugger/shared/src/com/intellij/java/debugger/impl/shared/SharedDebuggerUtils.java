// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class SharedDebuggerUtils {
  /**
   * @see com.intellij.debugger.engine.DebuggerUtils#translateStringValue(String)
   */
  public static String translateStringValue(final String str) {
    int length = str.length();
    final StringBuilder buffer = new StringBuilder();
    StringUtil.escapeStringCharacters(length, str, buffer);
    return buffer.toString();
  }
}
