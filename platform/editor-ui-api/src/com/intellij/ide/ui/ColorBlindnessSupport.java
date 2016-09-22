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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.ImageFilter;
import java.util.EnumMap;

/**
 * This is a base class for plugin extensions supporting color-blindness.
 * Only one extension will be chosen for every type of {@link ColorBlindness}.
 * <br/>
 * You can specify a custom implementation to support color-blindness in the {@code plugin.xml} file:<pre>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;
 * &nbsp;&nbsp;&nbsp;&nbsp;&lt;supportColorBlindness implementation="my.package.MyColorBlindnessSupport"/&gt;
 * &lt;/extensions&gt;</pre>
 *
 * @author Sergey.Malenkov
 */
public class ColorBlindnessSupport {
  /**
   * @return an instance of extension to support color-blindness
   */
  @NotNull
  public static ColorBlindnessSupport get() {
    return Lazy.VALUE;
  }

  /**
   * @param blindness available color-blindness to check if it is supported
   * @return a display name of the specified color-blindness or {@null} if it is not supported
   */
  @Nullable
  public String getDisplayName(@NotNull ColorBlindness blindness) {
    return ColorBlindness.deuteranopia.equals(blindness) ? blindness.name() : null;
  }

  /**
   * @param blindness currently selected color-blindness or {@null} if it is not selected
   * @return an image filter for the specified color-blindness or {@null} if it is not supported
   */
  @Nullable
  public ImageFilter getFilter(@Nullable ColorBlindness blindness) {
    return null;
  }

  private static final class Lazy extends ColorBlindnessSupport {
    private static final ColorBlindnessSupport VALUE = create();

    private static ColorBlindnessSupport create() {
      ColorBlindnessSupport[] extensions = (ColorBlindnessSupport[])Extensions.getExtensions("com.intellij.supportColorBlindness");
      if (extensions.length == 1) return extensions[0];
      if (extensions.length > 0) {
        Logger logger = Logger.getInstance(ColorBlindnessSupport.class);
        EnumMap<ColorBlindness, ColorBlindnessSupport> map = new EnumMap<>(ColorBlindness.class);
        for (ColorBlindness blindness : ColorBlindness.values()) {
          ColorBlindnessSupport support = null;
          for (ColorBlindnessSupport ext : extensions) {
            if (support == null && ext.getDisplayName(blindness) != null) support = ext;
          }
          if (support != null) {
            map.put(blindness, support);
            if (logger.isDebugEnabled()) logger.debug(toString("use", blindness, support));
            for (ColorBlindnessSupport ext : extensions) {
              if (support != ext && ext.getDisplayName(blindness) != null) logger.warn(toString("ignore", blindness, ext));
            }
          }
        }
        if (!map.isEmpty()) {
          return new Lazy(map);
        }
      }
      return new ColorBlindnessSupport();
    }

    private static String toString(String prefix, ColorBlindness blindness, ColorBlindnessSupport support) {
      return prefix + " " + blindness.name() + " from " + support.getClass();
    }

    private final EnumMap<ColorBlindness, ColorBlindnessSupport> myMap;

    private Lazy(EnumMap<ColorBlindness, ColorBlindnessSupport> map) {
      myMap = map;
    }

    @Nullable
    @Override
    public String getDisplayName(@NotNull ColorBlindness blindness) {
      ColorBlindnessSupport support = myMap.get(blindness);
      return support == null ? null : support.getDisplayName(blindness);
    }

    @Nullable
    @Override
    public ImageFilter getFilter(@Nullable ColorBlindness blindness) {
      if (blindness == null) return null;
      ColorBlindnessSupport support = myMap.get(blindness);
      return support == null ? null : support.getFilter(blindness);
    }
  }
}
