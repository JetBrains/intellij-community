// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

class ColorConverter {
  public int convert(int red, int green, int blue, int alpha) {
    return (fix(red) << 16) | (fix(green) << 8) | fix(blue) | (fix(alpha) << 24);
  }

  public int convert(int argb) {
    return convert(0xFF & (argb >> 16), 0xFF & (argb >> 8), 0xFF & argb, 0xFF & (argb >> 24));
  }

  private static int fix(int value) {
    return Math.max(0, Math.min(value, 255));
  }
}
