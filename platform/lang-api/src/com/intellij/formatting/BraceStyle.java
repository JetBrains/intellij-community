// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;

public enum  BraceStyle {
  EndOfLine(CommonCodeStyleSettings.END_OF_LINE),
  NextLine(CommonCodeStyleSettings.NEXT_LINE),
  NextLineShifted(CommonCodeStyleSettings.NEXT_LINE_SHIFTED),
  NextLineEachShifted(CommonCodeStyleSettings.NEXT_LINE_SHIFTED2),
  NextLineIfWrapped(CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED);

  private final int value;

  BraceStyle(int value) {
    this.value = value;
  }

  public final int intValue() {
    return value;
  }

  public static BraceStyle fromInt(int value) {
    for (BraceStyle style : values()) {
      if (style.intValue() == value) {
        return style;
      }
    }
    throw new InvalidDataException("Unknown brace style integer value " + value);
  }
}
