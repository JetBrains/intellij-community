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

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Used to override Caret Painting Color and for non-block carets their Width
 */
final public class CaretAttributes {
  final public static Key<CaretAttributes> KEY = new Key<>("CaretAttributes");
  final public static CaretAttributes NULL = new CaretAttributes(null, Weight.NORMAL);

  public enum Weight {
    THIN(-1),
    NORMAL(0),
    HEAVY(1);

    final private int delta;
    
    Weight(int delta) {
      this.delta = delta;
    }

    public int getDelta() {
      return delta;
    }
  }
  
  final private @Nullable Color myColor;
  final private Weight myWeight ;

  public CaretAttributes(@Nullable Color color, Weight weight) {
    myColor = color;
    myWeight = weight;
  }

  @Nullable
  public Color getColor() {
    return myColor;
  }

  public Weight getWeight() {
    return myWeight;
  }
  
  public int getWidth(int width) {
    return width + myWeight.delta < 1 ? 1 : width +myWeight.delta;
  }
}
