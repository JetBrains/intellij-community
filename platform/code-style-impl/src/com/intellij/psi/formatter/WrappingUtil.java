// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.formatter;

import com.intellij.formatting.Wrap;
import com.intellij.formatting.WrapType;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;

/**
 * Misc. code wrapping functions
 */
public final class WrappingUtil {

  private WrappingUtil() {
  }

  public static boolean shouldWrap(int setting) {
    return setting != CommonCodeStyleSettings.DO_NOT_WRAP;
  }

  public static Wrap createWrap(int setting) {
    return Wrap.createWrap(getWrapType(setting), true);
  }

  public static WrapType getWrapType(int setting) {
    return switch (setting) {
      case CommonCodeStyleSettings.WRAP_ALWAYS -> WrapType.ALWAYS;
      case CommonCodeStyleSettings.WRAP_AS_NEEDED -> WrapType.NORMAL;
      case CommonCodeStyleSettings.DO_NOT_WRAP -> WrapType.NONE;
      default -> WrapType.CHOP_DOWN_IF_LONG;
    };
  }

}
