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
package com.intellij.ide.ui;

import org.jetbrains.annotations.NonNls;

import java.awt.image.ImageFilter;
import java.awt.image.RGBImageFilter;

final class MatrixFilter extends RGBImageFilter {
  public static ImageFilter get(ColorBlindness blindness) {
    if (blindness == ColorBlindness.protanopia) return protanopia;
    if (blindness == ColorBlindness.deuteranopia) return deuteranopia;
    if (blindness == ColorBlindness.tritanopia) return tritanopia;
    return null;
  }

  public static final ImageFilter protanopia = forProtanopia(null);
  public static final ImageFilter deuteranopia = forDeuteranopia(null);
  public static final ImageFilter tritanopia = forTritanopia(null);

  public static ImageFilter forProtanopia(Double weight) {
    return new MatrixFilter("Protanopia (correction matrix)", new MatrixConverter(weight, ColorBlindnessMatrix.Protanopia.MATRIX));
  }

  public static ImageFilter forDeuteranopia(Double weight) {
    return new MatrixFilter("Deuteranopia (correction matrix)", new MatrixConverter(weight, ColorBlindnessMatrix.Deuteranopia.MATRIX));
  }

  public static ImageFilter forTritanopia(Double weight) {
    return new MatrixFilter("Tritanopia (correction matrix)", new MatrixConverter(weight, ColorBlindnessMatrix.Tritanopia.MATRIX));
  }

  private final String myName;
  private final ColorConverter myConverter;

  private MatrixFilter(String name, ColorConverter converter) {
    myName = name;
    myConverter = converter;
  }

  @Override
  @NonNls
  public String toString() {
    return myName;
  }

  @Override
  public int filterRGB(int x, int y, int rgb) {
    return myConverter.convert(rgb);
  }
}
