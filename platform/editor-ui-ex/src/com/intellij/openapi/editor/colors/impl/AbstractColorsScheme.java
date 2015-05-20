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

/**
 * @author Yura Cangea
 */
package com.intellij.openapi.editor.colors.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.FontSize;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.JBUI;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

import static com.intellij.openapi.editor.colors.CodeInsightColors.*;
import static com.intellij.openapi.editor.colors.EditorColors.*;
import static com.intellij.openapi.util.Couple.of;
import static com.intellij.ui.ColorUtil.fromHex;

public abstract class AbstractColorsScheme implements EditorColorsScheme {
  private static final String OS_VALUE_PREFIX = SystemInfo.isWindows ? "windows" : SystemInfo.isMac ? "mac" : "linux";
  private static final int CURR_VERSION = 141;

  private static final FontSize DEFAULT_FONT_SIZE = FontSize.SMALL;

  protected EditorColorsScheme myParentScheme;

  protected FontSize myQuickDocFontSize = DEFAULT_FONT_SIZE;
  protected float myLineSpacing;

  @NotNull private final Map<EditorFontType, Font> myFonts                  = new EnumMap<EditorFontType, Font>(EditorFontType.class);
  @NotNull private final FontPreferences           myFontPreferences        = new FontPreferences();
  @NotNull private final FontPreferences           myConsoleFontPreferences = new FontPreferences();

  private String myFallbackFontName;
  private String mySchemeName;

  private float myConsoleLineSpacing = -1;

  // version influences XML format and triggers migration
  private int myVersion = CURR_VERSION;

  protected Map<ColorKey, Color>                   myColorsMap     = ContainerUtilRt.newHashMap();
  protected Map<TextAttributesKey, TextAttributes> myAttributesMap = ContainerUtilRt.newHashMap();

  @NonNls private static final String EDITOR_FONT       = "font";
  @NonNls private static final String CONSOLE_FONT      = "console-font";
  @NonNls private static final String EDITOR_FONT_NAME  = "EDITOR_FONT_NAME";
  @NonNls private static final String CONSOLE_FONT_NAME = "CONSOLE_FONT_NAME";
  private                      Color  myDeprecatedBackgroundColor    = null;
  @NonNls private static final String SCHEME_ELEMENT                 = "scheme";
  @NonNls public static final  String NAME_ATTR                      = "name";
  @NonNls private static final String VERSION_ATTR                   = "version";
  @NonNls private static final String DEFAULT_SCHEME_ATTR            = "default_scheme";
  @NonNls private static final String PARENT_SCHEME_ATTR             = "parent_scheme";
  @NonNls private static final String OPTION_ELEMENT                 = "option";
  @NonNls private static final String COLORS_ELEMENT                 = "colors";
  @NonNls private static final String ATTRIBUTES_ELEMENT             = "attributes";
  @NonNls private static final String VALUE_ELEMENT                  = "value";
  @NonNls private static final String BACKGROUND_COLOR_NAME          = "BACKGROUND";
  @NonNls private static final String LINE_SPACING                   = "LINE_SPACING";
  @NonNls private static final String CONSOLE_LINE_SPACING           = "CONSOLE_LINE_SPACING";
  @NonNls private static final String EDITOR_FONT_SIZE               = "EDITOR_FONT_SIZE";
  @NonNls private static final String CONSOLE_FONT_SIZE              = "CONSOLE_FONT_SIZE";
  @NonNls private static final String EDITOR_QUICK_JAVADOC_FONT_SIZE = "EDITOR_QUICK_DOC_FONT_SIZE";

  protected AbstractColorsScheme(EditorColorsScheme parentScheme) {
    myParentScheme = parentScheme;
    myFontPreferences.setChangeListener(new Runnable() {
      @Override
      public void run() {
        initFonts();
      }
    });
  }

  public AbstractColorsScheme() {
  }

  @NotNull
  @Override
  public Color getDefaultBackground() {
    final Color c = getAttributes(HighlighterColors.TEXT).getBackgroundColor();
    return c != null ? c : Color.white;
  }

  @NotNull
  @Override
  public Color getDefaultForeground() {
    final Color c = getAttributes(HighlighterColors.TEXT).getForegroundColor();
    return c != null ? c : Color.black;
  }

  @NotNull
  @Override
  public String getName() {
    return mySchemeName;
  }

  @Override
  public void setFont(EditorFontType key, Font font) {
    myFonts.put(key, font);
  }

  @Override
  public abstract Object clone();

  public void copyTo(AbstractColorsScheme newScheme) {
    myFontPreferences.copyTo(newScheme.myFontPreferences);
    newScheme.myLineSpacing = myLineSpacing;
    newScheme.myQuickDocFontSize = myQuickDocFontSize;
    myConsoleFontPreferences.copyTo(newScheme.myConsoleFontPreferences);
    newScheme.myConsoleLineSpacing = myConsoleLineSpacing;

    final Set<EditorFontType> types = myFonts.keySet();
    for (EditorFontType type : types) {
      Font font = myFonts.get(type);
      newScheme.setFont(type, font);
    }

    newScheme.myAttributesMap = new HashMap<TextAttributesKey, TextAttributes>(myAttributesMap);
    newScheme.myColorsMap = new HashMap<ColorKey, Color>(myColorsMap);
    newScheme.myVersion = myVersion;
  }

  @Override
  public void setEditorFontName(String fontName) {
    int editorFontSize = getEditorFontSize();
    myFontPreferences.clear();
    myFontPreferences.register(fontName, editorFontSize);
    initFonts();
  }

  @Override
  public void setEditorFontSize(int fontSize) {
    myFontPreferences.register(getEditorFontName(), fontSize);
    initFonts();
  }
  
  @Override
  public void setQuickDocFontSize(@NotNull FontSize fontSize) {
    myQuickDocFontSize = fontSize;
  }

  @Override
  public void setLineSpacing(float lineSpacing) {
    myLineSpacing = lineSpacing;
  }

  @Override
  public Font getFont(EditorFontType key) {
    if (UISettings.getInstance().PRESENTATION_MODE) {
      final Font font = myFonts.get(key);
      return new Font(font.getName(), font.getStyle(), UISettings.getInstance().PRESENTATION_MODE_FONT_SIZE);
    }
    return myFonts.get(key);
  }

  @Override
  public void setName(@NotNull String name) {
    mySchemeName = name;
  }

  @NotNull
  @Override
  public FontPreferences getFontPreferences() {
    return myFontPreferences;
  }

  @Override
  public void setFontPreferences(@NotNull FontPreferences preferences) {
    preferences.copyTo(myFontPreferences);
    initFonts();
  }

  @Override
  public String getEditorFontName() {
    if (myFallbackFontName != null) {
      return myFallbackFontName;
    }
    return myFontPreferences.getFontFamily();
  }

  @Override
  public int getEditorFontSize() {
    return myFontPreferences.getSize(getEditorFontName());
  }

  @NotNull
  @Override
  public FontSize getQuickDocFontSize() {
    return myQuickDocFontSize;
  }
  
  @Override
  public float getLineSpacing() {
    float spacing = myLineSpacing;
    return spacing <= 0 ? 1.0f : spacing;
  }

  protected void initFonts() {
    String editorFontName = getEditorFontName();
    int editorFontSize = getEditorFontSize();
    
    myFallbackFontName = FontPreferences.getFallbackName(editorFontName, editorFontSize, myParentScheme);
    if (myFallbackFontName != null) {
      editorFontName = myFallbackFontName;
    }
    Font plainFont = new Font(editorFontName, Font.PLAIN, editorFontSize);
    Font boldFont = new Font(editorFontName, Font.BOLD, editorFontSize);
    Font italicFont = new Font(editorFontName, Font.ITALIC, editorFontSize);
    Font boldItalicFont = new Font(editorFontName, Font.BOLD | Font.ITALIC, editorFontSize);

    myFonts.put(EditorFontType.PLAIN, plainFont);
    myFonts.put(EditorFontType.BOLD, boldFont);
    myFonts.put(EditorFontType.ITALIC, italicFont);
    myFonts.put(EditorFontType.BOLD_ITALIC, boldItalicFont);

    String consoleFontName = getConsoleFontName();
    int consoleFontSize = getConsoleFontSize();

    Font consolePlainFont = new Font(consoleFontName, Font.PLAIN, consoleFontSize);
    Font consoleBoldFont = new Font(consoleFontName, Font.BOLD, consoleFontSize);
    Font consoleItalicFont = new Font(consoleFontName, Font.ITALIC, consoleFontSize);
    Font consoleBoldItalicFont = new Font(consoleFontName, Font.BOLD | Font.ITALIC, consoleFontSize);

    myFonts.put(EditorFontType.CONSOLE_PLAIN, consolePlainFont);
    myFonts.put(EditorFontType.CONSOLE_BOLD, consoleBoldFont);
    myFonts.put(EditorFontType.CONSOLE_ITALIC, consoleItalicFont);
    myFonts.put(EditorFontType.CONSOLE_BOLD_ITALIC, consoleBoldItalicFont);
  }

  public String toString() {
    return getName();
  }

  public void readExternal(Element parentNode) {
    if (SCHEME_ELEMENT.equals(parentNode.getName())) {
      readScheme(parentNode);
    }
    else {
      for (Element element : parentNode.getChildren(SCHEME_ELEMENT)) {
        readScheme(element);
      }
    }
    initFonts();
    myVersion = CURR_VERSION;
  }

  private void readScheme(Element node) {
    myDeprecatedBackgroundColor = null;
    if (!SCHEME_ELEMENT.equals(node.getName())) {
      return;
    }

    setName(node.getAttributeValue(NAME_ATTR));
    int readVersion = Integer.parseInt(node.getAttributeValue(VERSION_ATTR, "0"));
    if (readVersion > CURR_VERSION) {
      throw new IllegalStateException("Unsupported color scheme version: " + readVersion);
    }

    myVersion = readVersion;
    String isDefaultScheme = node.getAttributeValue(DEFAULT_SCHEME_ATTR);
    if (isDefaultScheme == null || !Boolean.parseBoolean(isDefaultScheme)) {
      myParentScheme = DefaultColorSchemesManager.getInstance().getScheme(node.getAttributeValue(PARENT_SCHEME_ATTR, DEFAULT_SCHEME_NAME));
    }

    for (final Object o : node.getChildren()) {
      Element childNode = (Element)o;
      String childName = childNode.getName();
      if (OPTION_ELEMENT.equals(childName)) {
        readSettings(childNode);
      }
      else if (EDITOR_FONT.equals(childName)) {
        readFontSettings(childNode, myFontPreferences);
      }
      else if (CONSOLE_FONT.equals(childName)) {
        readFontSettings(childNode, myConsoleFontPreferences);
      }
      else if (COLORS_ELEMENT.equals(childName)) {
        readColors(childNode);
      }
      else if (ATTRIBUTES_ELEMENT.equals(childName)) {
        readAttributes(childNode);
      }
    }

    if (myDeprecatedBackgroundColor != null) {
      TextAttributes textAttributes = myAttributesMap.get(HighlighterColors.TEXT);
      if (textAttributes == null) {
        textAttributes = new TextAttributes(Color.black, myDeprecatedBackgroundColor, null, EffectType.BOXED, Font.PLAIN);
        myAttributesMap.put(HighlighterColors.TEXT, textAttributes);
      }
      else {
        textAttributes.setBackgroundColor(myDeprecatedBackgroundColor);
      }
    }

    if (myConsoleFontPreferences.getEffectiveFontFamilies().isEmpty()) {
      myFontPreferences.copyTo(myConsoleFontPreferences);
    }

    initFonts();
  }

  public void readAttributes(@NotNull Element childNode) {
    for (Element e : childNode.getChildren(OPTION_ELEMENT)) {
      TextAttributesKey name = TextAttributesKey.find(e.getAttributeValue(NAME_ATTR));
      TextAttributes attr = new TextAttributes(e.getChild(VALUE_ELEMENT));
      myAttributesMap.put(name, attr);
      migrateErrorStripeColorFrom14(name, attr);
    }
  }

  private void migrateErrorStripeColorFrom14(@NotNull TextAttributesKey name, @NotNull TextAttributes attr) {
    if (myVersion >= 141 || myParentScheme == null) return;

    Couple<Color> m = DEFAULT_STRIPE_COLORS.get(name.getExternalName());
    if (m != null && Comparing.equal(m.first, attr.getErrorStripeColor())) {
      attr.setErrorStripeColor(m.second);
    }
  }

  @Deprecated
  @SuppressWarnings("unused")
  public static final Map<String, Color> DEFAULT_ERROR_STRIPE_COLOR = new THashMap<String, Color>();

  @SuppressWarnings("UseJBColor")
  private static final Map<String, Couple<Color>> DEFAULT_STRIPE_COLORS = new THashMap<String, Couple<Color>>() {
    {
      put(ERRORS_ATTRIBUTES.getExternalName(),                        of(Color.red,          fromHex("CF5B56")));
      put(WARNINGS_ATTRIBUTES.getExternalName(),                      of(Color.yellow,       fromHex("EBC700")));
      put("EXECUTIONPOINT_ATTRIBUTES",                                of(Color.blue,         fromHex("3763b0")));
      put(IDENTIFIER_UNDER_CARET_ATTRIBUTES.getExternalName(),        of(fromHex("CCCFFF"),  fromHex("BAA8FF")));
      put(WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES.getExternalName(),  of(fromHex("FFCCE5"),  fromHex("F0ADF0")));
      put(TEXT_SEARCH_RESULT_ATTRIBUTES.getExternalName(),            of(fromHex("586E75"),  fromHex("71B362")));
      put(TODO_DEFAULT_ATTRIBUTES.getExternalName(),                  of(fromHex("268BD2"),  fromHex("54AAE3")));
    }
  };
  
  private void readColors(Element childNode) {
    for (final Object o : childNode.getChildren(OPTION_ELEMENT)) {
      Element colorElement = (Element)o;
      Color valueColor = readColorValue(colorElement);
      final String colorName = colorElement.getAttributeValue(NAME_ATTR);
      if (BACKGROUND_COLOR_NAME.equals(colorName)) {
        // This setting has been deprecated to usages of HighlighterColors.TEXT attributes.
        myDeprecatedBackgroundColor = valueColor;
      }

      ColorKey name = ColorKey.find(colorName);
      myColorsMap.put(name, valueColor);
    }
  }

  private static Color readColorValue(final Element colorElement) {
    String value = getValue(colorElement);
    Color valueColor = null;
    if (value != null && value.trim().length() > 0) {
      try {
        valueColor = new Color(Integer.parseInt(value, 16));
      }
      catch (NumberFormatException ignored) {
      }
    }
    return valueColor;
  }

  private void readSettings(Element childNode) {
    String name = childNode.getAttributeValue(NAME_ATTR);
    String value = getValue(childNode);
    if (LINE_SPACING.equals(name)) {
      myLineSpacing = Float.parseFloat(value);
    }
    else if (EDITOR_FONT_SIZE.equals(name)) {
      setEditorFontSize(JBUI.scale(Integer.parseInt(value)));
    }
    else if (EDITOR_FONT_NAME.equals(name)) {
      setEditorFontName(value);
    }
    else if (CONSOLE_LINE_SPACING.equals(name)) {
      setConsoleLineSpacing(Float.parseFloat(value));
    }
    else if (CONSOLE_FONT_SIZE.equals(name)) {
      setConsoleFontSize(JBUI.scale(Integer.parseInt(value)));
    }
    else if (CONSOLE_FONT_NAME.equals(name)) {
      setConsoleFontName(value);
    }
    else if (EDITOR_QUICK_JAVADOC_FONT_SIZE.equals(name)) {
      myQuickDocFontSize = FontSize.valueOf(value);
    }
  }

  private static void readFontSettings(@NotNull Element element, @NotNull FontPreferences preferences) {
    List children = element.getChildren(OPTION_ELEMENT);
    String fontFamily = null;
    int size = -1;
    for (Object child : children) {
      Element e = (Element)child;
      if (EDITOR_FONT_NAME.equals(e.getAttributeValue(NAME_ATTR))) {
        fontFamily = getValue(e);
      }
      else if (EDITOR_FONT_SIZE.equals(e.getAttributeValue(NAME_ATTR))) {
        try {
          size = JBUI.scale(Integer.parseInt(getValue(e)));
        }
        catch (NumberFormatException ex) {
          // ignore
        }
      }
    }
    if (fontFamily != null && size > 1) {
      preferences.register(fontFamily, size);
    }
    else if (fontFamily != null) {
      preferences.addFontFamily(fontFamily);
    }
  }

  private static String getValue(Element e) {
    final String value = e.getAttributeValue(OS_VALUE_PREFIX);
    return value == null ? e.getAttributeValue(VALUE_ELEMENT) : value;
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    parentNode.setAttribute(NAME_ATTR, getName());
    parentNode.setAttribute(VERSION_ATTR, Integer.toString(myVersion));

    if (myParentScheme != null) {
      parentNode.setAttribute(PARENT_SCHEME_ATTR, myParentScheme.getName());
    }

    Element element = new Element(OPTION_ELEMENT);
    element.setAttribute(NAME_ATTR, LINE_SPACING);
    element.setAttribute(VALUE_ELEMENT, String.valueOf(getLineSpacing()));
    parentNode.addContent(element);

    // IJ has used a 'single customizable font' mode for ages. That's why we want to support that format now, when it's possible
    // to specify fonts sequence (see getFontPreferences()), there are big chances that many clients still will use a single font.
    // That's why we want to use old format when zero or one font is selected and 'extended' format otherwise.
    boolean useOldFontFormat = myFontPreferences.getEffectiveFontFamilies().size() <= 1;
    if (useOldFontFormat) {
      element = new Element(OPTION_ELEMENT);
      element.setAttribute(NAME_ATTR, EDITOR_FONT_SIZE);
      element.setAttribute(VALUE_ELEMENT, String.valueOf(getEditorFontSize() / JBUI.scale(1)));
      parentNode.addContent(element);
    }
    else {
      writeFontPreferences(EDITOR_FONT, parentNode, myFontPreferences);
    }

    if (!myFontPreferences.equals(myConsoleFontPreferences)) {
      if (myConsoleFontPreferences.getEffectiveFontFamilies().size() <= 1) {
        element = new Element(OPTION_ELEMENT);
        element.setAttribute(NAME_ATTR, CONSOLE_FONT_NAME);
        element.setAttribute(VALUE_ELEMENT, getConsoleFontName());
        parentNode.addContent(element);

        if (getConsoleFontSize() != getEditorFontSize()) {
          element = new Element(OPTION_ELEMENT);
          element.setAttribute(NAME_ATTR, CONSOLE_FONT_SIZE);
          element.setAttribute(VALUE_ELEMENT, Integer.toString(getConsoleFontSize() / JBUI.scale(1)));
          parentNode.addContent(element);
        }
      }
      else {
        writeFontPreferences(CONSOLE_FONT, parentNode, myConsoleFontPreferences);
      }
    }

    if (getConsoleLineSpacing() != getLineSpacing()) {
      element = new Element(OPTION_ELEMENT);
      element.setAttribute(NAME_ATTR, CONSOLE_LINE_SPACING);
      element.setAttribute(VALUE_ELEMENT, Float.toString(getConsoleLineSpacing()));
      parentNode.addContent(element);
    }

    if (DEFAULT_FONT_SIZE != getQuickDocFontSize()) {
      element = new Element(OPTION_ELEMENT);
      element.setAttribute(NAME_ATTR, EDITOR_QUICK_JAVADOC_FONT_SIZE);
      element.setAttribute(VALUE_ELEMENT, getQuickDocFontSize().toString());
      parentNode.addContent(element);
    }

    if (useOldFontFormat) {
      element = new Element(OPTION_ELEMENT);
      element.setAttribute(NAME_ATTR, EDITOR_FONT_NAME);
      element.setAttribute(VALUE_ELEMENT, getEditorFontName());
      parentNode.addContent(element);
    }

    Element colorElements = new Element(COLORS_ELEMENT);
    Element attrElements = new Element(ATTRIBUTES_ELEMENT);

    writeColors(colorElements);
    writeAttributes(attrElements);

    if (colorElements.getChildren().size() > 0) {
      parentNode.addContent(colorElements);
    }
    if (attrElements.getChildren().size() > 0) {
      parentNode.addContent(attrElements);
    }
  }

  private static void writeFontPreferences(@NotNull String key, @NotNull Element parent, @NotNull FontPreferences preferences) {
    for (String fontFamily : preferences.getRealFontFamilies()) {
      Element element = new Element(key);
      Element e = new Element(OPTION_ELEMENT);
      e.setAttribute(NAME_ATTR, EDITOR_FONT_NAME);
      e.setAttribute(VALUE_ELEMENT, fontFamily);
      element.addContent(e);

      e = new Element(OPTION_ELEMENT);
      e.setAttribute(NAME_ATTR, EDITOR_FONT_SIZE);
      e.setAttribute(VALUE_ELEMENT, String.valueOf(preferences.getSize(fontFamily)));
      element.addContent(e);

      parent.addContent(element);
    }
  }
  
  private static boolean haveToWrite(final TextAttributesKey key, final TextAttributes value, final TextAttributes defaultAttribute) {
    return !(key.getFallbackAttributeKey() != null && value.isFallbackEnabled()) && !value.equals(defaultAttribute);
  }

  private void writeAttributes(Element attrElements) throws WriteExternalException {
    List<TextAttributesKey> list = new ArrayList<TextAttributesKey>(myAttributesMap.keySet());
    Collections.sort(list);

    for (TextAttributesKey key: list) {
      TextAttributes defaultAttr = myParentScheme != null ? myParentScheme.getAttributes(key) : new TextAttributes();
      TextAttributes value = myAttributesMap.get(key);
      if (!haveToWrite(key,value,defaultAttr)) continue;
      Element element = new Element(OPTION_ELEMENT);
      element.setAttribute(NAME_ATTR, key.getExternalName());
      Element valueElement = new Element(VALUE_ELEMENT);
      value.writeExternal(valueElement);
      element.addContent(valueElement);
      attrElements.addContent(element);
    }
  }

  protected Color getOwnColor(ColorKey key) {
    return myColorsMap.get(key);
  }

  private void writeColors(Element colorElements) {
    List<ColorKey> list = new ArrayList<ColorKey>(myColorsMap.keySet());
    Collections.sort(list);

    for (ColorKey key : list) {
      if (haveToWrite(key)) {
        Color value = myColorsMap.get(key);
        Element element = new Element(OPTION_ELEMENT);
        element.setAttribute(NAME_ATTR, key.getExternalName());
        element.setAttribute(VALUE_ELEMENT, value != null ? Integer.toString(value.getRGB() & 0xFFFFFF, 16) : "");
        colorElements.addContent(element);
      }
    }
  }

  private boolean haveToWrite(final ColorKey key) {
    Color value = myColorsMap.get(key);
    if (myParentScheme != null) {
      if (myParentScheme instanceof AbstractColorsScheme) {
        if (Comparing.equal(((AbstractColorsScheme)myParentScheme).getOwnColor(key), value) && ((AbstractColorsScheme)myParentScheme).myColorsMap.containsKey(key)) {
          return false;
        }
      }
      else {
        if (Comparing.equal((myParentScheme).getColor(key), value)) {
          return false;
        }
      }
    }
    return true;

  }

  @NotNull
  @Override
  public FontPreferences getConsoleFontPreferences() {
    return myConsoleFontPreferences;
  }

  @Override
  public void setConsoleFontPreferences(@NotNull FontPreferences preferences) {
    preferences.copyTo(myConsoleFontPreferences);
    initFonts();
  }

  @Override
  public String getConsoleFontName() {
    return myConsoleFontPreferences.getFontFamily();
  }

  @Override
  public void setConsoleFontName(String fontName) {
    int consoleFontSize = getConsoleFontSize();
    myConsoleFontPreferences.clear();
    myConsoleFontPreferences.register(fontName, consoleFontSize);
  }

  @Override
  public int getConsoleFontSize() {
    String font = getConsoleFontName();
    UISettings uiSettings = UISettings.getInstance();
    if ((uiSettings == null || !uiSettings.PRESENTATION_MODE) && myConsoleFontPreferences.hasSize(font)) {
      return myConsoleFontPreferences.getSize(font);
    }
    return getEditorFontSize();
  }

  @Override
  public void setConsoleFontSize(int fontSize) {
    myConsoleFontPreferences.register(getConsoleFontName(), fontSize);
    initFonts();
  }

  @Override
  public float getConsoleLineSpacing() {
    float consoleLineSpacing = myConsoleLineSpacing;
    if (consoleLineSpacing == -1) {
      return getLineSpacing();
    }
    return consoleLineSpacing;
  }

  @Override
  public void setConsoleLineSpacing(float lineSpacing) {
    myConsoleLineSpacing = lineSpacing;
  }

  protected TextAttributes getFallbackAttributes(TextAttributesKey fallbackKey) {
    if (fallbackKey == null) return null;
    if (myAttributesMap.containsKey(fallbackKey)) {
      TextAttributes fallbackAttributes = myAttributesMap.get(fallbackKey);
      if (fallbackAttributes != null && (!fallbackAttributes.isFallbackEnabled() || fallbackKey.getFallbackAttributeKey() == null)) {
        return fallbackAttributes;
      }
    }
    return getFallbackAttributes(fallbackKey.getFallbackAttributeKey());
  }

}
