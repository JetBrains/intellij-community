// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.openapi.diagnostic.Logger;

import java.awt.*;
import java.lang.reflect.Field;

public final class FontSubpixelResolution {
  private static final Logger LOG = Logger.getInstance(FontSubpixelResolution.class);

  public static final Dimension RESOLUTION;
  public static final boolean ENABLED;

  static {
    Dimension resolution;
    try {
      Field field;
      Class<?> clazz = Class.forName("sun.font.FontUtilities");
      try {
        field = clazz.getDeclaredField("subpixelResolution");
      } catch (NoSuchFieldException ignore) {
        //noinspection JavaReflectionMemberAccess
        field = clazz.getDeclaredField("supplementarySubpixelGlyphResolution");
      }
      field.setAccessible(true);
      resolution = (Dimension)field.get(null);
    }
    catch (ReflectiveOperationException ignore) {
      resolution = null;
    }
    catch (Throwable e) {
      resolution = null;
      LOG.error("Couldn't get font subpixel resolution settings", e);
    }
    RESOLUTION = resolution;
    ENABLED = RESOLUTION != null && (RESOLUTION.width > 1 || RESOLUTION.height > 1);
  }
}
