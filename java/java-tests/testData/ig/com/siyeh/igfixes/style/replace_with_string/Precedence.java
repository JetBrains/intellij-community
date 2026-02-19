package com.siyeh.igfixes.style.replace_with_string;

import java.awt.*;

class Precedence {
  public static String toRgbColor( final Color color) {
    return new StringB<caret>uilder("rgba(").append(color.getRed()).append(',')
      .append(color.getGreen()).append(',').append(color.getBlue())
      .append(',').append(color.getAlpha() == 0 ? '0' : String.format("0.%2d", (int) (color.getAlpha() / 255.0 * 100)))
      .append(')').append(9*9).toString();
  }
}