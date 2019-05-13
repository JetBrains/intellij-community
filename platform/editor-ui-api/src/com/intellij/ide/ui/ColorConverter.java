package com.intellij.ide.ui;

/**
 * @author Sergey.Malenkov
 */
class ColorConverter {
  public int convert(int red, int green, int blue, int alpha) {
    return (fix(red) << 16) | (fix(green) << 8) | fix(blue) | (fix(alpha) << 24);
  }

  public int convert(int argb) {
    return convert(0xFF & (argb >> 16), 0xFF & (argb >> 8), 0xFF & argb, 0xFF & (argb >> 24));
  }

  private static int fix(int value) {
    return value < 0 ? 0 : value > 255 ? 255 : value;
  }
}
