// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;

import java.awt.image.ImageFilter;
import java.awt.image.RGBImageFilter;

@ApiStatus.Internal
public final class MatrixFilter extends RGBImageFilter {
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
  public @NonNls String toString() {
    return myName;
  }

  @Override
  public int filterRGB(int x, int y, int rgb) {
    return myConverter.convert(rgb);
  }
}
