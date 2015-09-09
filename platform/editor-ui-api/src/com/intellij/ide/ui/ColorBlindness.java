/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/**
 * @author Sergey.Malenkov
 */
public enum ColorBlindness {
  /**
   * Lacking the long-wavelength sensitive retinal cones,
   * those with this condition are unable to distinguish
   * between colors in the green–yellow–red section of the spectrum.
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
   * between colors in the green–yellow–red section of the spectrum.
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

  public final String key;

  ColorBlindness(String key) {
    this.key = key;
  }
}
