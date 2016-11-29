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
import org.jetbrains.annotations.Nullable;

import java.awt.image.ImageFilter;
import java.util.EnumMap;

/**
 * This is a base class for plugin extensions supporting color-blindness.
 * Only one extension will be chosen for every type of {@link ColorBlindness}.
 * <br/>
 * You can specify a custom implementation to support color-blindness in the {@code plugin.xml} file:<pre>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;
 * &nbsp;&nbsp;&nbsp;&nbsp;&lt;protanopiaSupport implementation="my.package.RedColorIssuesSupport"/&gt;
 * &nbsp;&nbsp;&nbsp;&nbsp;&lt;deuteranopiaSupport implementation="my.package.GreenColorIssuesSupport"/&gt;
 * &nbsp;&nbsp;&nbsp;&nbsp;&lt;tritanopiaSupport implementation="my.package.BlueColorIssuesSupport"/&gt;
 * &nbsp;&nbsp;&nbsp;&nbsp;&lt;achromatopsiaSupport implementation="my.package.ColorVisionIssuesSupport"/&gt;
 * &lt;/extensions&gt;</pre>
 *
 * @author Sergey.Malenkov
 */
public class ColorBlindnessSupport {
  /**
   * Returns an instance of extension to support the specified color-blindness.
   */
  @Nullable
  public static ColorBlindnessSupport get(@Nullable ColorBlindness blindness) {
    return blindness == null ? null : Lazy.MAP.get(blindness);
  }

  /**
   * Returns an image filter for the supported color-blindness.
   */
  @Nullable
  public ImageFilter getFilter() {
    return null;
  }

  private static final class Lazy {
    private static final EnumMap<ColorBlindness, ColorBlindnessSupport> MAP = create();

    private static EnumMap<ColorBlindness, ColorBlindnessSupport> create() {
      EnumMap<ColorBlindness, ColorBlindnessSupport> map = new EnumMap<>(ColorBlindness.class);
      init(map, ColorBlindness.protanopia, "com.intellij.protanopiaSupport");
      init(map, ColorBlindness.deuteranopia, "com.intellij.deuteranopiaSupport");
      init(map, ColorBlindness.tritanopia, "com.intellij.tritanopiaSupport");
      init(map, ColorBlindness.achromatopsia, "com.intellij.achromatopsiaSupport");
      if (map.isEmpty()) map.put(ColorBlindness.deuteranopia, new ColorBlindnessSupport());
      return map;
    }

    private static void init(EnumMap<ColorBlindness, ColorBlindnessSupport> map, ColorBlindness blindness, String extensionName) {
      ColorBlindnessSupport[] extensions = (ColorBlindnessSupport[])Extensions.getExtensions(extensionName);
      ColorBlindnessSupport support = null;
      for (ColorBlindnessSupport ext : extensions) {
        if (support == null) support = ext;
      }
      if (support != null) {
        map.put(blindness, support);
        Logger logger = Logger.getInstance(ColorBlindnessSupport.class);
        if (logger.isDebugEnabled()) logger.debug(toString("use", blindness, support));
        for (ColorBlindnessSupport ext : extensions) {
          if (support != ext) logger.warn(toString("ignore", blindness, ext));
        }
      }
    }

    private static String toString(String prefix, ColorBlindness blindness, ColorBlindnessSupport support) {
      return prefix + " " + blindness.name() + " from " + support.getClass();
    }
  }
}
