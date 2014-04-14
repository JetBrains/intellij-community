/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor.richcopy;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Java logical font names (like 'Monospaced') don't necessarily make sense for other applications, so we try to map those fonts to
 * the corresponding physical font names.
 */
public class FontMapper {
  private static final Logger LOG = Logger.getInstance("#" + FontMapper.class.getName());

  private static final String[] logicalFontsToMap = {Font.DIALOG, Font.DIALOG_INPUT, Font.MONOSPACED, Font.SERIF, Font.SANS_SERIF};
  private static final Map<String, String> logicalToPhysicalMapping = new HashMap<String, String>();

  static {
    try {
      Object fontManager = null;
      try {
        fontManager = Class.forName("sun.font.FontManagerFactory").getMethod("getInstance").invoke(null);
      }
      catch (ClassNotFoundException e) {
        // expected for JRE 1.6. FontManager.findFont2D method is static there, so leaving fontManager value as null will work
      }
      Method findFontMethod = Class.forName("sun.font.FontManager").getMethod("findFont2D", String.class, int.class, int.class);
      for (String logicalFont : logicalFontsToMap) {
        String physicalFont = null;
        Object font2D = findFontMethod.invoke(fontManager, logicalFont, Font.PLAIN, 0);
        if (font2D == null) {
          continue;
        }
        String fontClassName = font2D.getClass().getName();
        if ("sun.font.CompositeFont".equals(fontClassName)) { // Windows and Linux case
          Object physicalFontObject = Class.forName("sun.font.CompositeFont").getMethod("getSlotFont", int.class).invoke(font2D, 0);
          physicalFont = (String)Class.forName("sun.font.Font2D").getMethod("getFamilyName", Locale.class).invoke(physicalFontObject, Locale.getDefault());
        }
        else if ("sun.font.CFont".equals(fontClassName)) { // MacOS case
          Field field = Class.forName("sun.font.CFont").getDeclaredField("nativeFontName");
          field.setAccessible(true);
          physicalFont = (String)field.get(font2D);
        }
        if (physicalFont != null) {
          logicalToPhysicalMapping.put(logicalFont, physicalFont);
        }
      }
    }
    catch (Throwable e) {
      LOG.warn("Failed to determine logical to physical font mappings");
    }
  }

  public static
  @NotNull
  String getPhysicalFontName(@NotNull String logicalFontName) {
    String mapped = logicalToPhysicalMapping.get(logicalFontName);
    return mapped == null ? logicalFontName : mapped;
  }
}
