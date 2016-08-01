/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeHighlighting;

import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ColorGenerator {
  @NotNull
  public static List<Color> generateLinearColorSequence(@NotNull List<Color> anchorColors, int colorsBetweenAnchors) {
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

  @NotNull
  public static List<Color> generateLinearColorSequence(@NotNull Color color1, @NotNull Color color2, int colorsBetweenAnchors) {
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
    int value = (int)(val1 + ((val2 - val1) * ratio));
    return Math.max(Math.min(value, 255), 0);
  }
}
