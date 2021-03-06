// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;

public enum ForceBraces {
  Never(CommonCodeStyleSettings.DO_NOT_FORCE),
  IfMultiline(CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE),
  Always(CommonCodeStyleSettings.FORCE_BRACES_ALWAYS);

  private final int myIntValue;

  ForceBraces(int value) {
    myIntValue = value;
  }

  public int intValue() {
    return myIntValue;
  }

  public static ForceBraces fromInt(int value) {
    for (ForceBraces mode : values()) {
      if (mode.intValue() == value) {
        return mode;
      }
    }
    throw new InvalidDataException("Unknown brace style integer value " + value);
  }
}
