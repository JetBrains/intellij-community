// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import com.intellij.openapi.editor.PlatformEditorBundle;
import org.jetbrains.annotations.PropertyKey;

public enum ColorBlindness {
  /**
   * Lacking the long-wavelength sensitive retinal cones,
   * those with this condition are unable to distinguish
   * between colors in the green-yellow-red section of the spectrum.
   * Protanopes are more likely to confuse
   * <ul>
   * <li>black with many shades of red;</li>
   * <li>dark brown with dark green, dark orange and dark red;</li>
   * <li>some blues with some reds, purples and dark pinks;</li>
   * <li>mid-greens with some oranges.</li>
   * </ul>
   */
  protanopia("color.blindness.protanopia.name"),
  /**
   * Lacking the medium-wavelength sensitive retinal cones,
   * those with this condition are unable to distinguish
   * between colors in the green-yellow-red section of the spectrum.
   * Deuteranopes are more likely to confuse
   * <ul>
   * <li>mid-reds with mid-greens;</li>
   * <li>blue-greens with grey and mid-pinks;</li>
   * <li>bright greens with yellows;</li>
   * <li>pale pinks with light grey;</li>
   * <li>mid-reds with mid-brown</li>
   * <li>light blues with lilac.</li>
   * </ul>
   */
  deuteranopia("color.blindness.deuteranopia.name"),
  /**
   * Lacking the short-wavelength sensitive retinal cones,
   * those affected see short-wavelength colors (blue, indigo and a spectral violet)
   * greenish and drastically dimmed, some of these colors even as black.
   * Tritanopes are more likely to confuse
   * <ul>
   * <li>light blues with greys;</li>
   * <li>dark purples with black;</li>
   * <li>mid-greens with blues;</li>
   * <li>oranges with reds.</li>
   * </ul>
   */
  tritanopia("color.blindness.tritanopia.name"),
  /**
   * Total color blindness is defined as the inability to see color.
   */
  achromatopsia("color.blindness.achromatopsia.name");

  public final @PropertyKey(resourceBundle = PlatformEditorBundle.BUNDLE) String key;

  ColorBlindness(@PropertyKey(resourceBundle = PlatformEditorBundle.BUNDLE) String key) {
    this.key = key;
  }
}
