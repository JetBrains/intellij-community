// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.ide.ui.ColorBlindness;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.ModifiableFontPreferences;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Ref;
import com.intellij.util.JdomKt;
import com.intellij.util.SystemProperties;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import static com.intellij.openapi.editor.colors.impl.AbstractColorsScheme.CURR_VERSION;
import static com.intellij.openapi.editor.colors.impl.AbstractColorsScheme.ENABLE_RUNTIME_SCHEME_COLOR_WRAPPER_OPTION;
import static com.intellij.openapi.editor.colors.impl.AbstractColorsScheme.INHERITED_ATTRS_MARKER;
import static com.intellij.openapi.editor.colors.impl.AbstractColorsScheme.INHERITED_COLOR_MARKER;
import static com.intellij.openapi.editor.colors.impl.AbstractColorsScheme.META_INFO_PARTIAL;
import static com.intellij.openapi.editor.colors.impl.AbstractColorsScheme.NAME_ATTR;
import static com.intellij.openapi.editor.colors.impl.AbstractColorsScheme.NULL_COLOR_MARKER;
import static com.intellij.openapi.editor.colors.impl.AbstractColorsScheme.colorsEqual;

/**
 * Reads and writes an {@link AbstractColorsScheme} as a JDOM element.
 * Each read creates its own {@link TextAttributesReader}, so two reads share no state.
 */
@SuppressWarnings("UseJBColor")
final class ColorsSchemeSerDe {
  private static final @NonNls String EDITOR_FONT = "font";
  private static final @NonNls String CONSOLE_FONT = "console-font";
  private static final @NonNls String EDITOR_FONT_NAME = "EDITOR_FONT_NAME";
  private static final @NonNls String CONSOLE_FONT_NAME = "CONSOLE_FONT_NAME";
  private static final @NonNls String SCHEME_ELEMENT = "scheme";
  private static final @NonNls String VERSION_ATTR = "version";
  private static final @NonNls String BASE_ATTRIBUTES_ATTR = "baseAttributes";
  private static final @NonNls String DEFAULT_SCHEME_ATTR = "default_scheme";
  private static final @NonNls String PARENT_SCHEME_ATTR = "parent_scheme";
  private static final @NonNls String OPTION_ELEMENT = "option";
  private static final @NonNls String COLORS_ELEMENT = "colors";
  private static final @NonNls String ATTRIBUTES_ELEMENT = "attributes";
  private static final @NonNls String VALUE_ELEMENT = "value";
  private static final @NonNls String BACKGROUND_COLOR_NAME = "BACKGROUND";
  private static final @NonNls String LINE_SPACING = "LINE_SPACING";
  private static final @NonNls String CONSOLE_LINE_SPACING = "CONSOLE_LINE_SPACING";
  private static final @NonNls String FONT_SCALE = "FONT_SCALE";
  private static final @NonNls String EDITOR_FONT_SIZE = "EDITOR_FONT_SIZE";
  private static final @NonNls String CONSOLE_FONT_SIZE = "CONSOLE_FONT_SIZE";
  private static final @NonNls String EDITOR_LIGATURES = "EDITOR_LIGATURES";
  private static final @NonNls String CONSOLE_LIGATURES = "CONSOLE_LIGATURES";
  private static final @NonNls String META_INFO_ELEMENT = "metaInfo";
  private static final @NonNls String PROPERTY_ELEMENT = "property";
  private static final @NonNls String PROPERTY_NAME_ATTR = "name";

  private ColorsSchemeSerDe() {
  }

  /**
   * Reads {@code parentNode} into {@code scheme}.
   * The node is a scheme element, or an element whose children are scheme elements.
   */
  static void readExternal(@NotNull AbstractColorsScheme scheme, @NotNull Element parentNode) {
    TextAttributesReader valueReader = createValueReader();
    if (SCHEME_ELEMENT.equals(parentNode.getName())) {
      readScheme(scheme, parentNode, valueReader);
    } else {
      List<Element> children = parentNode.getChildren(SCHEME_ELEMENT);
      if (children.isEmpty()) {
        throw new InvalidDataException("Scheme is not valid");
      }
      for (Element element : children) {
        readScheme(scheme, element, valueReader);
      }
    }
  }

  /**
   * Adds the text attributes of the option elements of {@code childNode} to {@code scheme}.
   */
  static void readAttributes(@NotNull AbstractColorsScheme scheme, @NotNull Element childNode) {
    Map<String, TextAttributes> attributes = new HashMap<>();
    readAttributes(childNode, attributes, createValueReader());
    scheme.updateSnapshot(current -> current.withAllAttributes(attributes));
  }

  /**
   * Adds the colors of the option elements of {@code childNode} to {@code scheme}.
   */
  static void readColors(@NotNull AbstractColorsScheme scheme, @NotNull Element childNode) {
    Map<ColorKey, Color> colors = new HashMap<>();
    readColors(childNode, colors, new Ref<>(), createValueReader());
    scheme.updateSnapshot(current -> current.withAllColors(colors));
  }

  /**
   * Writes {@code scheme} into {@code parentNode} and clears the save flag of the scheme.
   * It clears the flag before it reads the scheme, so a change during the write marks the scheme again.
   * A write that fails marks the scheme again too.
   */
  static void writeExternal(@NotNull AbstractColorsScheme scheme, @NotNull Element parentNode) {
    scheme.setSaveNeeded(false);
    boolean written = false;
    try {
      writeScheme(scheme, parentNode);
      written = true;
    } finally {
      if (!written) {
        scheme.setSaveNeeded(true);
      }
    }
  }

  /**
   * Writes {@code scheme} into {@code parentNode}.
   * The fonts, the colors and the text attributes come from one snapshot of the scheme.
   */
  private static void writeScheme(@NotNull AbstractColorsScheme scheme, @NotNull Element parentNode) {
    ColorsSchemeSnapshot current = scheme.getSnapshot();
    FontPreferences fontPreferences = current.fontPreferences();
    FontPreferences consoleFontPreferences = current.consoleFontPreferences();
    FontPreferences editorFontPreferences = scheme.fontPreferencesOf(current);
    int editorFontSize = editorFontPreferences.getSize(editorFontPreferences.getFontFamily());
    float editorFontSize2D = editorFontPreferences.getSize2D(editorFontPreferences.getFontFamily());
    float lineSpacing = editorFontPreferences.getLineSpacing();
    boolean editorDelegating = current.useAppFontPreferencesInEditor();
    boolean consoleDelegating = current.useEditorFontPreferencesInConsole();
    Properties metaInfo = scheme.getMetaProperties();
    parentNode.setAttribute(NAME_ATTR, scheme.getName());
    parentNode.setAttribute(VERSION_ATTR, Integer.toString(CURR_VERSION));
    /*
     * FONT_SCALE is used to correctly identify the font size in both the JRE-managed HiDPI mode and
     * the IDE-managed HiDPI mode: {@link UIUtil#isJreHiDPIEnabled()}. Also, it helps to distinguish
     * the "hidpi-aware" scheme version from the previous one. Namely, the absence of the FONT_SCALE
     * attribute in the scheme indicates the previous "hidpi-unaware" scheme and the restored font size
     * is reset to default. It's assumed this (transition case) happens only once, after which the IDE
     * will be able to restore the font size according to its scale and the IDE HiDPI mode. The default
     * FONT_SCALE value should also be written for that reason.
     */
    if (!editorDelegating || !consoleDelegating) {
      // must precede font options
      JdomKt.addOptionTag(parentNode, FONT_SCALE, String.valueOf(UISettings.getDefFontScale()));
    }
    EditorColorsScheme parentScheme = current.parentScheme();
    if (parentScheme != null && parentScheme != EmptyColorScheme.getEmptyScheme()) {
      parentNode.setAttribute(PARENT_SCHEME_ATTR, parentScheme.getName());
    }
    EditorColorsScheme baseScheme = getBaseScheme(scheme, current);
    if (!"true".equals(metaInfo.getProperty(META_INFO_PARTIAL)) &&
        !scheme.isReadOnly() &&
        baseScheme != parentScheme) {
      metaInfo.setProperty(META_INFO_PARTIAL, "true");
    }
    if (!metaInfo.isEmpty()) {
      parentNode.addContent(metaInfoToElement(metaInfo));
    }
    // IJ has used a 'single customizable font' mode for ages. That's why we want to support that format now, when it's possible
    // to specify a font sequence (see getFontPreferences()), there are big chances that many clients still will use a single font.
    // That's why we want to use an old format when zero or one font is selected and 'extended' format otherwise.
    boolean useOldFontFormat = fontPreferences.getEffectiveFontFamilies().size() <= 1;
    if (!editorDelegating) {
      JdomKt.addOptionTag(parentNode, LINE_SPACING, String.valueOf(lineSpacing));
      if (useOldFontFormat) {
        JdomKt.addOptionTag(parentNode, EDITOR_FONT_SIZE, String.valueOf(editorFontSize));
        JdomKt.addOptionTag(parentNode, EDITOR_FONT_NAME, fontPreferences.getFontFamily());
      } else {
        writeFontPreferences(EDITOR_FONT, parentNode, fontPreferences);
      }
      writeLigaturesPreferences(parentNode, fontPreferences, EDITOR_LIGATURES);
    }
    if (!consoleDelegating) {
      if (consoleFontPreferences.getEffectiveFontFamilies().size() <= 1) {
        JdomKt.addOptionTag(parentNode, CONSOLE_FONT_NAME, consoleFontPreferences.getFontFamily());
        // Write font size as integer for compatibility
        float consoleFontSize2D = AbstractColorsScheme.consoleFontSize2D(consoleFontPreferences, editorFontSize2D);
        int consoleFontSize = (int)(consoleFontSize2D + 0.5);
        if (consoleFontSize != editorFontSize) {
          JdomKt.addOptionTag(parentNode, CONSOLE_FONT_SIZE, Integer.toString(consoleFontSize));
        }
      } else {
        writeFontPreferences(CONSOLE_FONT, parentNode, consoleFontPreferences);
      }
      writeLigaturesPreferences(parentNode, consoleFontPreferences, CONSOLE_LIGATURES);
      float consoleLineSpacing = consoleFontPreferences.getLineSpacing();
      if (editorDelegating || consoleLineSpacing != lineSpacing) {
        JdomKt.addOptionTag(parentNode, CONSOLE_LINE_SPACING, Float.toString(consoleLineSpacing));
      }
    }
    Element colorElements = new Element(COLORS_ELEMENT);
    Element attrElements = new Element(ATTRIBUTES_ELEMENT);
    writeColors(colorElements, current.colors(), baseScheme);
    writeAttributes(attrElements, current.attributes(), baseScheme);
    if (!colorElements.getChildren().isEmpty()) {
      parentNode.addContent(colorElements);
    }
    if (!attrElements.getChildren().isEmpty()) {
      parentNode.addContent(attrElements);
    }
  }

  /**
   * Returns a reader that reads the values for the color blindness mode of the IDE.
   */
  private static @NotNull TextAttributesReader createValueReader() {
    UISettings settings = UISettings.getInstanceOrNull();
    ColorBlindness blindness = settings == null ? null : settings.getColorBlindness();
    TextAttributesReader valueReader = new TextAttributesReader();
    valueReader.setAttribute(blindness == null ? null : blindness.name());
    return valueReader;
  }

  private static boolean markColorIds() {
    return SystemProperties.getBooleanProperty(ENABLE_RUNTIME_SCHEME_COLOR_WRAPPER_OPTION, false);
  }

  private static void readScheme(
    @NotNull AbstractColorsScheme scheme,
    @NotNull Element node,
    @NotNull TextAttributesReader valueReader
  ) {
    if (!SCHEME_ELEMENT.equals(node.getName())) {
      return;
    }
    scheme.setName(node.getAttributeValue(NAME_ATTR));
    int readVersion = Integer.parseInt(node.getAttributeValue(VERSION_ATTR, "0"));
    if (readVersion > CURR_VERSION) {
      throw new IllegalStateException("Unsupported color scheme version: " + readVersion);
    }
    String isDefaultScheme = node.getAttributeValue(DEFAULT_SCHEME_ATTR);
    boolean isDefault = Boolean.parseBoolean(isDefaultScheme);
    EditorColorsScheme newParent = isDefault
                                   ? null
                                   : getDefaultScheme(node.getAttributeValue(PARENT_SCHEME_ATTR, EmptyColorScheme.getSchemeName()));
    Properties metaInfo = new Properties();
    ColorsSchemeFontDraft fonts = scheme.createFontDraft(scheme.getSnapshot());
    Ref<Float> fontScale = new Ref<>();
    boolean clearEditorFonts = true;
    boolean clearConsoleFonts = true;
    Map<ColorKey, Color> colors = new HashMap<>();
    Map<String, TextAttributes> attributes = new HashMap<>();
    Ref<Color> backgroundColor = new Ref<>();
    for (Element childNode : node.getChildren()) {
      String childName = childNode.getName();
      switch (childName) {
        case OPTION_ELEMENT -> readSettings(fonts, childNode, isDefault, fontScale, valueReader);
        case EDITOR_FONT -> {
          Float scale = fontScale.get();
          boolean clearFonts = clearEditorFonts;
          fonts.changeFont(draft -> readFontSettings(draft, childNode, isDefault, scale, clearFonts, valueReader));
          clearEditorFonts = false;
        }
        case CONSOLE_FONT -> {
          Float scale = fontScale.get();
          boolean clearFonts = clearConsoleFonts;
          fonts.changeConsoleFont(draft -> readFontSettings(draft, childNode, isDefault, scale, clearFonts, valueReader));
          clearConsoleFonts = false;
        }
        case COLORS_ELEMENT -> readColors(childNode, colors, backgroundColor, valueReader);
        case ATTRIBUTES_ELEMENT -> readAttributes(childNode, attributes, valueReader);
        case META_INFO_ELEMENT -> readMetaInfo(childNode, metaInfo);
      }
    }
    fillEmptyConsoleFont(fonts);
    ColorsSchemeSnapshot readFonts = fonts.snapshot();
    Color deprecatedBackgroundColor = backgroundColor.get();
    scheme.replaceMetaProperties(metaInfo);
    scheme.updateSnapshot(current -> {
      ColorsSchemeSnapshot loaded = current
        .withFontsOf(readFonts)
        .withAllColors(colors)
        .withAllAttributes(attributes);
      if (newParent != null) {
        loaded = loaded.withParentScheme(newParent);
      }
      return withDeprecatedBackgroundColor(loaded, deprecatedBackgroundColor);
    });
  }

  /**
   * Gives the editor font to an own console font without font families.
   */
  private static void fillEmptyConsoleFont(@NotNull ColorsSchemeFontDraft fonts) {
    ColorsSchemeSnapshot current = fonts.snapshot();
    if (!current.useEditorFontPreferencesInConsole() &&
        current.consoleFontPreferences().getEffectiveFontFamilies().isEmpty()) {
      FontPreferences editorFontPreferences = fonts.editorFontPreferences();
      fonts.changeConsoleFont(editorFontPreferences::copyTo);
    }
  }

  private static void readMetaInfo(@NotNull Element metaInfoElement, @NotNull Properties metaInfo) {
    metaInfo.clear();
    for (Element e : metaInfoElement.getChildren()) {
      if (PROPERTY_ELEMENT.equals(e.getName())) {
        String propertyName = e.getAttributeValue(PROPERTY_NAME_ATTR);
        if (propertyName != null) {
          metaInfo.setProperty(propertyName, e.getText());
        }
      }
    }
  }

  private static void readAttributes(
    @NotNull Element childNode,
    @NotNull Map<String, TextAttributes> attributes,
    @NotNull TextAttributesReader valueReader
  ) {
    boolean markColorIds = markColorIds();
    for (Element e : childNode.getChildren(OPTION_ELEMENT)) {
      String keyName = e.getAttributeValue(NAME_ATTR);
      Element valueElement = e.getChild(VALUE_ELEMENT);
      TextAttributes attr = valueElement != null
                            ? valueReader.readAttributes(valueElement, markColorIds ? keyName : null)
                            : e.getAttributeValue(BASE_ATTRIBUTES_ATTR) != null ? INHERITED_ATTRS_MARKER : null;
      if (attr != null) {
        attributes.put(keyName, attr);
      }
    }
  }

  private static void readColors(
    @NotNull Element childNode,
    @NotNull Map<ColorKey, Color> colors,
    @NotNull Ref<Color> backgroundColor,
    @NotNull TextAttributesReader valueReader
  ) {
    boolean markColorIds = markColorIds();
    for (Element colorElement : childNode.getChildren(OPTION_ELEMENT)) {
      String keyName = colorElement.getAttributeValue(NAME_ATTR);
      Color valueColor = valueReader.read(Color.class, colorElement);
      if (valueColor != null && keyName != null && markColorIds) {
        valueColor = new ColorKeyColor(valueColor, keyName);
      }
      if (valueColor == null && colorElement.getAttributeValue(BASE_ATTRIBUTES_ATTR) != null) {
        valueColor = INHERITED_COLOR_MARKER;
      }
      if (BACKGROUND_COLOR_NAME.equals(keyName)) {
        // This setting has been deprecated to usages of HighlighterColors.TEXT attributes.
        backgroundColor.set(valueColor);
      }
      ColorKey name = ColorKey.find(Objects.requireNonNull(keyName));
      colors.put(name, valueColor == null ? NULL_COLOR_MARKER : valueColor);
    }
  }

  private static void readSettings(
    @NotNull ColorsSchemeFontDraft fonts,
    @NotNull Element childNode,
    boolean isDefault,
    @NotNull Ref<Float> fontScale,
    @NotNull TextAttributesReader valueReader
  ) {
    switch (childNode.getAttributeValue(NAME_ATTR)) {
      case FONT_SCALE -> fontScale.set(valueReader.read(Float.class, childNode));
      case LINE_SPACING -> {
        Float value = valueReader.read(Float.class, childNode);
        if (value != null) {
          fonts.setLineSpacing(value);
        }
      }
      case EDITOR_FONT_SIZE -> {
        float value = readFontSize(childNode, isDefault, fontScale.get(), valueReader);
        if (value > 0) {
          fonts.setEditorFontSize(value);
        }
      }
      case EDITOR_FONT_NAME -> {
        String value = valueReader.read(String.class, childNode);
        if (value != null) {
          fonts.setEditorFontName(value);
        }
      }
      case CONSOLE_LINE_SPACING -> {
        Float value = valueReader.read(Float.class, childNode);
        if (value != null) {
          fonts.setConsoleLineSpacing(value);
        }
      }
      case CONSOLE_FONT_SIZE -> {
        float value = readFontSize(childNode, isDefault, fontScale.get(), valueReader);
        if (value > 0) {
          fonts.setConsoleFontSize(value);
        }
      }
      case CONSOLE_FONT_NAME -> {
        String value = valueReader.read(String.class, childNode);
        if (value != null) {
          fonts.setConsoleFontName(value);
        }
      }
      case EDITOR_LIGATURES -> {
        Boolean value = valueReader.read(Boolean.class, childNode);
        if (value != null) {
          fonts.setUseLigatures(value);
        }
      }
      case CONSOLE_LIGATURES -> {
        Boolean value = valueReader.read(Boolean.class, childNode);
        if (value != null) {
          fonts.setConsoleUseLigatures(value);
        }
      }
    }
  }

  private static float readFontSize(
    Element element,
    boolean isDefault,
    Float fontScale,
    @NotNull TextAttributesReader valueReader
  ) {
    if (isDefault) {
      return UISettings.getDefFontSize();
    }
    // Use integer size for compatibility
    Integer intSize = valueReader.read(Integer.class, element);
    if (intSize == null) {
      return -1;
    }
    return UISettings.restoreFontSize(intSize.floatValue(), fontScale);
  }

  private static void readFontSettings(
    @NotNull ModifiableFontPreferences preferences,
    @NotNull Element element,
    boolean isDefaultScheme,
    @Nullable Float fontScale,
    boolean clearFonts,
    @NotNull TextAttributesReader valueReader
  ) {
    if (clearFonts) {
      preferences.clearFonts();
    }
    List<Element> children = element.getChildren(OPTION_ELEMENT);
    String fontFamily = null;
    float size = -1;
    for (Element e : children) {
      if (EDITOR_FONT_NAME.equals(e.getAttributeValue(NAME_ATTR))) {
        fontFamily = valueReader.read(String.class, e);
      } else if (EDITOR_FONT_SIZE.equals(e.getAttributeValue(NAME_ATTR))) {
        size = readFontSize(e, isDefaultScheme, fontScale, valueReader);
      }
    }
    if (fontFamily != null && size > 1) {
      preferences.register(fontFamily, size);
    } else if (fontFamily != null) {
      preferences.addFontFamily(fontFamily);
    }
  }

  private static void writeAttributes(
    @NotNull Element attrElements,
    @NotNull Map<String, TextAttributes> attributesMap,
    @Nullable EditorColorsScheme baseScheme
  ) {
    List<Map.Entry<String, TextAttributes>> list = new ArrayList<>(attributesMap.entrySet());
    list.sort(Map.Entry.comparingByKey());
    for (Map.Entry<String, TextAttributes> entry : list) {
      String keyName = entry.getKey();
      TextAttributes attributes = entry.getValue();
      writeAttribute(attrElements, TextAttributesKey.find(keyName), attributes, baseScheme);
    }
  }

  private static void writeAttribute(
    @NotNull Element attrElements,
    @NotNull TextAttributesKey key,
    @NotNull TextAttributes attributes,
    @Nullable EditorColorsScheme baseScheme
  ) {
    if (attributes == INHERITED_ATTRS_MARKER) {
      TextAttributesKey baseKey = key.getFallbackAttributeKey();
      // IDEA-162774 do not store if inheritance = on in the parent scheme
      TextAttributes parentAttributes = baseScheme instanceof AbstractColorsScheme absScheme
                                        ? absScheme.getDirectlyDefinedAttributes(key)
                                        : null;
      boolean parentOverwritingInheritance = parentAttributes != null && parentAttributes != INHERITED_ATTRS_MARKER;
      if (parentOverwritingInheritance) {
        Element attributeElement = new Element(OPTION_ELEMENT)
          .setAttribute(NAME_ATTR, key.getExternalName())
          .setAttribute(BASE_ATTRIBUTES_ATTR, baseKey != null ? baseKey.getExternalName() : "");
        attrElements.addContent(attributeElement);
      }
      return;
    }
    if (baseScheme != null) {
      // fallback attributes must be not used, otherwise derived scheme as copy will not have such key
      TextAttributes parentAttributes = baseScheme instanceof AbstractColorsScheme absScheme
                                        ? absScheme.getDirectlyDefinedAttributes(key)
                                        : baseScheme.getAttributes(key);
      if (attributes.equals(parentAttributes)) {
        return;
      }
    }
    Element valueElement = new Element(VALUE_ELEMENT);
    attributes.writeExternal(valueElement);
    Element attributeElement = new Element(OPTION_ELEMENT)
      .setAttribute(NAME_ATTR, key.getExternalName())
      .addContent(valueElement);
    attrElements.addContent(attributeElement);
  }

  private static @NotNull Element metaInfoToElement(@NotNull Properties metaInfo) {
    Element metaInfoElement = new Element(META_INFO_ELEMENT);
    List<String> sortedPropertyNames = new ArrayList<>(metaInfo.stringPropertyNames());
    sortedPropertyNames.sort(null);
    for (String propertyName : sortedPropertyNames) {
      String value = metaInfo.getProperty(propertyName);
      Element propertyInfo = new Element(PROPERTY_ELEMENT);
      propertyInfo.setAttribute(PROPERTY_NAME_ATTR, propertyName);
      propertyInfo.setText(value);
      metaInfoElement.addContent(propertyInfo);
    }
    return metaInfoElement;
  }

  private static void writeColors(
    @NotNull Element colorElements,
    @NotNull Map<ColorKey, Color> colorMap,
    @Nullable EditorColorsScheme baseScheme
  ) {
    List<Map.Entry<ColorKey, Color>> list = new ArrayList<>(colorMap.entrySet());
    list.sort(Map.Entry.comparingByKey());
    for (Map.Entry<ColorKey, Color> entry : list) {
      writeColor(colorElements, entry.getKey(), entry.getValue(), baseScheme);
    }
  }

  private static void writeColor(
    @NotNull Element colorElements,
    @NotNull ColorKey key,
    @NotNull Color color,
    @Nullable EditorColorsScheme baseScheme
  ) {
    if (color == INHERITED_COLOR_MARKER) {
      ColorKey fallbackKey = key.getFallbackColorKey();
      Color parentFallback = baseScheme instanceof AbstractColorsScheme absScheme ? absScheme.getDirectlyDefinedColor(key) : null;
      boolean parentOverwritingInheritance = parentFallback != null && parentFallback != INHERITED_COLOR_MARKER;
      if (fallbackKey != null && parentOverwritingInheritance) {
        Element colorElement = new Element(OPTION_ELEMENT)
          .setAttribute(NAME_ATTR, key.getExternalName())
          .setAttribute(BASE_ATTRIBUTES_ATTR, fallbackKey.getExternalName());
        colorElements.addContent(colorElement);
      }
      return;
    }
    if (baseScheme != null) {
      Color parent = baseScheme instanceof AbstractColorsScheme absScheme
                     ? absScheme.getDirectlyDefinedColor(key)
                     : baseScheme.getColor(key);
      if (parent != null && colorsEqual(color, parent)) {
        return;
      }
    }
    String rgb = "";
    if (color != NULL_COLOR_MARKER) {
      rgb = String.format("%06X", 0xFFFFFF & color.getRGB());
      int alpha = 0xFF & color.getAlpha();
      if (alpha != 0xFF) {
        rgb += String.format("%02X", alpha);
      }
    }
    JdomKt.addOptionTag(colorElements, key.getExternalName(), rgb);
  }

  /**
   * Returns the scheme that the written values are compared with.
   * An editable copy is compared with its original scheme, and other schemes with their parent scheme.
   */
  private static @Nullable EditorColorsScheme getBaseScheme(
    @NotNull AbstractColorsScheme scheme,
    @NotNull ColorsSchemeSnapshot current
  ) {
    String schemeName = current.schemeName();
    if (schemeName != null && schemeName.startsWith(Scheme.EDITABLE_COPY_PREFIX)) {
      EditorColorsScheme original = scheme.getOriginal();
      if (original != null) {
        return original;
      }
    }
    return current.parentScheme();
  }

  private static @NotNull EditorColorsScheme getDefaultScheme(@NotNull String name) {
    DefaultColorSchemesManager manager = DefaultColorSchemesManager.getInstance();
    EditorColorsScheme defaultScheme = manager.getScheme(name);
    if (defaultScheme == null) {
      defaultScheme = new TemporaryParent(name);
    }
    return defaultScheme;
  }

  private static void writeLigaturesPreferences(
    Element parentNode,
    FontPreferences preferences,
    String optionName
  ) {
    if (preferences.useLigatures()) {
      JdomKt.addOptionTag(parentNode, optionName, String.valueOf(true));
    }
  }

  private static void writeFontPreferences(
    @NotNull String key,
    @NotNull Element parent,
    @NotNull FontPreferences preferences
  ) {
    for (String fontFamily : preferences.getRealFontFamilies()) {
      Element element = new Element(key);
      JdomKt.addOptionTag(element, EDITOR_FONT_NAME, fontFamily);
      // Write font size as integer for compatibility
      JdomKt.addOptionTag(element, EDITOR_FONT_SIZE, String.valueOf(preferences.getSize(fontFamily)));
      parent.addContent(element);
    }
  }

  /**
   * Returns {@code current} with {@code backgroundColor} in the text attributes of {@link HighlighterColors#TEXT}.
   * Returns {@code current} itself when {@code backgroundColor} is {@code null}.
   */
  private static @NotNull ColorsSchemeSnapshot withDeprecatedBackgroundColor(
    @NotNull ColorsSchemeSnapshot current,
    @Nullable Color backgroundColor
  ) {
    if (backgroundColor == null) {
      return current;
    }
    String textKeyName = HighlighterColors.TEXT.getExternalName();
    TextAttributes textAttributes = current.getAttributes(textKeyName);
    TextAttributes newTextAttributes;
    if (textAttributes == null) {
      newTextAttributes = new TextAttributes(Color.black, backgroundColor, null, EffectType.BOXED, Font.PLAIN);
    } else {
      // Other snapshots can share these text attributes, so only a copy gets the color
      newTextAttributes = textAttributes.clone();
      newTextAttributes.setBackgroundColor(backgroundColor);
    }
    return current.withAttributes(textKeyName, newTextAttributes);
  }
}
