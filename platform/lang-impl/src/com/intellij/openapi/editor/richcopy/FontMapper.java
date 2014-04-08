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

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Java logical font names (like 'Monospaced') don't necessarily make sense for other applications, so we try to map those fonts to
 * the corresponding physical font names.
 */
public class FontMapper {
  private static final Logger LOG = Logger.getInstance("#" + FontMapper.class.getName());

  private static final String MAC_OS_FONT_CLASS = "sun.font.CFont";
  private static final String[] logicalFontsToMap = {Font.DIALOG, Font.DIALOG_INPUT, Font.MONOSPACED, Font.SERIF, Font.SANS_SERIF};
  private static final Map<String, String> logicalToPhysicalMapping = new HashMap<String, String>();

  static {
    /*try {
      FontManager fontManager = FontManagerFactory.getInstance();
      for (String logicalFont : logicalFontsToMap) {
        String physicalFont = null;
        Font2D font2D = fontManager.findFont2D(logicalFont, OptionsConstants.DEFAULT_EDITOR_FONT_SIZE, 0);
        if (font2D == null) {
          continue;
        }
        else if (font2D instanceof CompositeFont && ((CompositeFont)font2D).getNumSlots() > 0) {
          physicalFont = ((CompositeFont)font2D).getSlotFont(0).getFamilyName(Locale.getDefault());
        }
        else if (font2D.getClass().getName().equals(MAC_OS_FONT_CLASS)) {
          Field field = Class.forName(MAC_OS_FONT_CLASS).getDeclaredField("nativeFontName");
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
    }*/
  }

  public static String getPhysicalFontName(String logicalFontName) {
    String mapped = logicalToPhysicalMapping.get(logicalFontName);
    return mapped == null ? logicalFontName : mapped;
  }
}
