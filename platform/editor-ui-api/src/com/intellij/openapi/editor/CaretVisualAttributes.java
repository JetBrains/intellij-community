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
package com.intellij.openapi.editor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Used to override caret painting color and, for non-block carets, their width.
 */
public final class CaretVisualAttributes {
  public static final CaretVisualAttributes DEFAULT = new CaretVisualAttributes(null, Weight.NORMAL);

  @Nullable
  private final Color myColor;
  @NotNull
  private final Weight myWeight;

  public CaretVisualAttributes(@Nullable Color color, @NotNull Weight weight) {
    myColor = color;
    myWeight = weight;
  }

  @Nullable
  public Color getColor() {
    return myColor;
  }

  @NotNull
  public Weight getWeight() {
    return myWeight;
  }

  public int getWidth(int defaultWidth) {
    return Math.max(1, defaultWidth + myWeight.delta);
  }

  public enum Weight {
    THIN(-1),
    NORMAL(0),
    HEAVY(1);

    private final int delta;

    Weight(int delta) {
      this.delta = delta;
    }

    public int getDelta() {
      return delta;
    }
  }
}
