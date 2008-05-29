package com.intellij.openapi.editor.impl;

import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;

/**
 * @author max
 */
public class ComplementaryFontsRegistry {
  private static ArrayList<String> ourFontNames;
  private static LinkedHashMap<FontKey, FontInfo> ourUsedFonts;
  private static FontKey ourSharedKeyInstance = new FontKey(null, 0, 0);
  private static FontInfo ourSharedDefaultFont;
  private static TIntHashSet ourUndisplayableChars = new TIntHashSet();

  private ComplementaryFontsRegistry() {
  }

  private static class FontKey {
    public String myFamilyName;
    public int mySize;
    public int myStyle;

    public FontKey(final String familyName, final int size, final int style) {
      myFamilyName = familyName;
      mySize = size;
      myStyle = style;
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      final FontKey fontKey = (FontKey)o;

      if (mySize != fontKey.mySize) return false;
      if (myStyle != fontKey.myStyle) return false;
      return myFamilyName.equals(fontKey.myFamilyName);
    }

    public int hashCode() {
      int result = myFamilyName.hashCode();
      result = 29 * result + mySize;
      result = 29 * result + myStyle;
      return result;
    }
  }

  @NonNls private static final String BOLD_SUFFIX = ".bold";

  @NonNls private static final String ITALIC_SUFFIX = ".italic";

  static {
    ourFontNames = new ArrayList<String>();
    GraphicsEnvironment graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
    String[] fontNames = graphicsEnvironment.getAvailableFontFamilyNames();
    for (final String fontName : fontNames) {
      if (!fontName.endsWith(BOLD_SUFFIX) && !fontName.endsWith(ITALIC_SUFFIX)) {
        ourFontNames.add(fontName);
      }
    }
    ourUsedFonts = new LinkedHashMap<FontKey, FontInfo>();
  }

  public static FontInfo getFontAbleToDisplay(char c, int size, int style, String defaultFontFamily) {
    if (ourSharedKeyInstance.mySize == size &&
        ourSharedKeyInstance.myStyle == style &&
        ourSharedKeyInstance.myFamilyName != null &&
        ourSharedKeyInstance.myFamilyName.equals(defaultFontFamily) &&
        ourSharedDefaultFont != null &&
        ( c < 128 ||
          ourSharedDefaultFont.canDisplay(c)
        )
       ) {
      return ourSharedDefaultFont;
    }

    ourSharedKeyInstance.myFamilyName = defaultFontFamily;
    ourSharedKeyInstance.mySize = size;
    ourSharedKeyInstance.myStyle = style;

    FontInfo defaultFont = ourUsedFonts.get(ourSharedKeyInstance);
    if (defaultFont == null) {
      defaultFont = new FontInfo(defaultFontFamily, size, style);
      ourUsedFonts.put(ourSharedKeyInstance, defaultFont);
      ourSharedKeyInstance = new FontKey(null, 0, 0);
    }

    ourSharedDefaultFont = defaultFont;
    if (c < 128 || defaultFont.canDisplay(c)) {
      return defaultFont;
    }

    if (ourUndisplayableChars.contains(c)) return defaultFont;

    final Collection<FontInfo> descriptors = ourUsedFonts.values();
    for (FontInfo font : descriptors) {
      if (font.getSize() == size && font.getStyle() == style && font.canDisplay(c)) {
        return font;
      }
    }

    for (int i = 0; i < ourFontNames.size(); i++) {
      String name = ourFontNames.get(i);
      FontInfo font = new FontInfo(name, size, style);
      if (font.canDisplay(c)) {
        ourUsedFonts.put(new FontKey(name, size, style), font);
        ourFontNames.remove(i);
        return font;
      }
    }

    ourUndisplayableChars.add(c);

    return defaultFont;
  }
}
