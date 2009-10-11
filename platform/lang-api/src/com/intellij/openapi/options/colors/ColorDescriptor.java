/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.options.colors;

import com.intellij.openapi.editor.colors.ColorKey;

/**
 * Describes a color which can be configured in a custom colors and fonts page.
 *
 * @see ColorSettingsPage#getColorDescriptors()
 */
public final class ColorDescriptor {
  public static final ColorDescriptor[] EMPTY_ARRAY = new ColorDescriptor[0];

  public static enum Kind {
    BACKGROUND,
    FOREGROUND
  }

  private final Kind myKind;
  private final String myDisplayName;
  private final ColorKey myKey;

  /**
   * Creates a color descriptor with the specified name and color key.
   *
   * @param displayName the name of the color shown in the colors list.
   * @param key         the color key for which the color is specified.
   * @param kind        the type of color corresponding to the color key (foreground or background).
   */
  public ColorDescriptor(String displayName, ColorKey key, Kind kind) {
    myKind = kind;
    myDisplayName = displayName;
    myKey = key;
  }

  /**
   * Returns the type of color corresponding to the color key (foreground or background).
   *
   * @return the type of color.
   */
  public Kind getKind() {
    return myKind;
  }

  /**
   * Returns the name of the color shown in the colors list.
   *
   * @return the name of the color.
   */
  public String getDisplayName() {
    return myDisplayName;
  }

  /**
   * Returns the color key for which the color is specified.
   *
   * @return the color key.
   */
  public ColorKey getKey() {
    return myKey;
  }
}