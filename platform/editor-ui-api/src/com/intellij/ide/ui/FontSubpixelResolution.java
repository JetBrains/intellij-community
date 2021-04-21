// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import java.awt.*;
import java.lang.reflect.Field;

public class FontSubpixelResolution {

  public static final Dimension RESOLUTION;
  public static final boolean ENABLED;

  static {
    Dimension resolution;
    try {
      //noinspection JavaReflectionMemberAccess
      Field field = Class.forName("sun.font.FontUtilities")
        .getDeclaredField("supplementarySubpixelGlyphResolution");
      field.setAccessible(true);
      resolution = (Dimension)field.get(null);
    }
    catch (ReflectiveOperationException ignore) {
      resolution = null;
    }
    RESOLUTION = resolution;
    ENABLED = RESOLUTION != null && (RESOLUTION.width > 1 || RESOLUTION.height > 1);
  }
}
