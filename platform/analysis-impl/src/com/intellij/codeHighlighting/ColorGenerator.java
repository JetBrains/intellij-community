// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeHighlighting;

import com.intellij.ui.JBColor;
import com.intellij.util.MathUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public final class ColorGenerator {
  public static @NotNull List<Color> generateLinearColorSequence(@NotNull List<? extends Color> anchorColors, int colorsBetweenAnchors) {
    assert colorsBetweenAnchors >= 0;
    if (anchorColors.isEmpty()) return Collections.singletonList(JBColor.GRAY);
    if (anchorColors.size() == 1) return Collections.singletonList(anchorColors.get(0));

    int segmentCount = anchorColors.size() - 1;
    List<Color> result = new ArrayList<>(anchorColors.size() + segmentCount * colorsBetweenAnchors);
    result.add(anchorColors.get(0));

    for (int i = 0; i < segmentCount; i++) {
      Color color1 = anchorColors.get(i);
      Color color2 = anchorColors.get(i + 1);

      List<Color> linearColors = generateLinearColorSequence(color1, color2, colorsBetweenAnchors);

      // skip first element from sequence to avoid duplication from connected segments
      result.addAll(linearColors.subList(1, linearColors.size()));
    }
    return result;
  }

  static @NotNull List<Color> generateLinearColorSequence(@NotNull Color color1, @NotNull Color color2, int colorsBetweenAnchors) {
    assert colorsBetweenAnchors >= 0;

    List<Color> result = new ArrayList<>(colorsBetweenAnchors + 2);
    result.add(color1);

    for (int i = 1; i <= colorsBetweenAnchors; i++) {
      float ratio = (float)i / (colorsBetweenAnchors + 1);

      //noinspection UseJBColor
      result.add(new Color(
        ratio(color1.getRed(), color2.getRed(), ratio),
        ratio(color1.getGreen(), color2.getGreen(), ratio),
        ratio(color1.getBlue(), color2.getBlue(), ratio)
      ));
    }

    result.add(color2);
    return result;
  }

  private static int ratio(int val1, int val2, float ratio) {
    int value = (int)(val1 + (val2 - val1) * ratio);
    return MathUtil.clamp(value, 0, 255);
  }
}
