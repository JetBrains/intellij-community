package com.siyeh.igfixes.style.replace_with_string;

import java.awt.*;

class Precedence {
  public static String toRgbColor( final Color color) {
    return "rgba(" + color.getRed() + ',' +
            color.getGreen() + ',' + color.getBlue() +
            ',' + (color.getAlpha() == 0 ? '0' : String.format("0.%2d", (int) (color.getAlpha() / 255.0 * 100))) +
            ')' + 9 * 9;
  }
}