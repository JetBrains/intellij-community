/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.openapi.editor.colors.impl;

import com.intellij.application.options.EditorFontsConstants;
import com.intellij.configurationStore.SerializableScheme;
import com.intellij.ide.ui.ColorBlindness;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.FontSize;
import com.intellij.openapi.options.SchemeState;
import com.intellij.openapi.util.*;
import com.intellij.util.JdomKt;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.intellij.openapi.editor.colors.CodeInsightColors.*;
import static com.intellij.openapi.editor.colors.EditorColors.*;
import static com.intellij.openapi.editor.markup.TextAttributes.USE_INHERITED_MARKER;
import static com.intellij.openapi.util.Couple.of;
import static com.intellij.ui.ColorUtil.fromHex;

@SuppressWarnings("UseJBColor")
public abstract class AbstractColorsScheme extends EditorFontCacheImpl implements EditorColorsScheme, SerializableScheme {
  private static final int CURR_VERSION = 142;

  // todo: unify with UIUtil.DEF_SYSTEM_FONT_SIZE
  private static final FontSize DEFAULT_FONT_SIZE = FontSize.SMALL;

  protected EditorColorsScheme myParentScheme;

  protected FontSize myQuickDocFontSize = DEFAULT_FONT_SIZE;

  @NotNull private FontPreferences                 myFontPreferences
    = new DelegatingFontPreferences(() -> AppEditorFontOptions.getInstance().getFontPreferences());
  @NotNull private FontPreferences                 myConsoleFontPreferences = new DelegatingFontPreferences(() -> myFontPreferences);

  private final ValueElementReader myValueReader = new TextAttributesReader();
  private String mySchemeName;

  private boolean myIsSaveNeeded;

  private boolean myCanBeDeleted = true;

  // version influences XML format and triggers migration
  private int myVersion = CURR_VERSION;

  protected Map<ColorKey, Color>                   myColorsMap     = ContainerUtilRt.newHashMap();
  protected Map<TextAttributesKey, TextAttributes> myAttributesMap = new THashMap<>();

  @NonNls private static final String EDITOR_FONT       = "font";
  @NonNls private static final String CONSOLE_FONT      = "console-font";
  @NonNls private static final String EDITOR_FONT_NAME  = "EDITOR_FONT_NAME";
  @NonNls private static final String CONSOLE_FONT_NAME = "CONSOLE_FONT_NAME";
  private                      Color  myDeprecatedBackgroundColor    = null;
  @NonNls private static final String SCHEME_ELEMENT                 = "scheme";
  @NonNls public static final  String NAME_ATTR                      = "name";
  @NonNls private static final String VERSION_ATTR                   = "version";
  @NonNls private static final String BASE_ATTRIBUTES_ATTR           = "baseAttributes";
  @NonNls private static final String DEFAULT_SCHEME_ATTR            = "default_scheme";
  @NonNls private static final String PARENT_SCHEME_ATTR             = "parent_scheme";
  @NonNls private static final String OPTION_ELEMENT                 = "option";
  @NonNls private static final String COLORS_ELEMENT                 = "colors";
  @NonNls private static final String ATTRIBUTES_ELEMENT             = "attributes";
  @NonNls private static final String VALUE_ELEMENT                  = "value";
  @NonNls private static final String BACKGROUND_COLOR_NAME          = "BACKGROUND";
  @NonNls private static final String LINE_SPACING                   = "LINE_SPACING";
  @NonNls private static final String CONSOLE_LINE_SPACING           = "CONSOLE_LINE_SPACING";
  @NonNls private static final String FONT_SCALE                     = "FONT_SCALE";
  @NonNls private static final String EDITOR_FONT_SIZE               = "EDITOR_FONT_SIZE";
  @NonNls private static final String CONSOLE_FONT_SIZE              = "CONSOLE_FONT_SIZE";
  @NonNls private static final String EDITOR_LIGATURES               = "EDITOR_LIGATURES";
  @NonNls private static final String CONSOLE_LIGATURES              = "CONSOLE_LIGATURES";
  @NonNls private static final String EDITOR_QUICK_JAVADOC_FONT_SIZE = "EDITOR_QUICK_DOC_FONT_SIZE";


  //region Meta info-related fields
  private final Properties myMetaInfo = new Properties();
  private final static SimpleDateFormat META_INFO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

  @NonNls private static final String META_INFO_ELEMENT       = "metaInfo";
  @NonNls private static final String PROPERTY_ELEMENT        = "property";
  @NonNls private static final String PROPERTY_NAME_ATTR      = "name";

  @NonNls private static final String META_INFO_CREATION_TIME = "created";
  @NonNls private static final String META_INFO_MODIFIED_TIME = "modified";
  @NonNls private static final String META_INFO_IDE           = "ide";
  @NonNls private static final String META_INFO_IDE_VERSION   = "ideVersion";
  @NonNls private static final String META_INFO_ORIGINAL      = "originalScheme";

  //endregion

  protected AbstractColorsScheme(EditorColorsScheme parentScheme) {
    myParentScheme = parentScheme;
  }

  public AbstractColorsScheme() {
  }

  public void setDefaultMetaInfo(@Nullable AbstractColorsScheme parentScheme) {
    myMetaInfo.setProperty(META_INFO_CREATION_TIME, META_INFO_DATE_FORMAT.format(new Date()));
    myMetaInfo.setProperty(META_INFO_IDE,           PlatformUtils.getPlatformPrefix());
    myMetaInfo.setProperty(META_INFO_IDE_VERSION,   ApplicationInfoEx.getInstanceEx().getStrictVersion());
    if (parentScheme != null && parentScheme != EmptyColorScheme.INSTANCE) {
      myMetaInfo.setProperty(META_INFO_ORIGINAL, parentScheme.getName());
    }
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
  }

  @Override
  public abstract Object clone();

  public void copyTo(AbstractColorsScheme newScheme) {
    newScheme.myQuickDocFontSize = myQuickDocFontSize;
    if (myConsoleFontPreferences instanceof DelegatingFontPreferences) {
      newScheme.setUseEditorFontPreferencesInConsole();
    }
    else {
      newScheme.setConsoleFontPreferences(myConsoleFontPreferences);
    }
    if (myFontPreferences instanceof DelegatingFontPreferences) {
      newScheme.setUseAppFontPreferencesInEditor();
    }
    else {
      newScheme.setFontPreferences(myFontPreferences);
    }

    newScheme.myAttributesMap = new THashMap<>(myAttributesMap);
    newScheme.myColorsMap = new HashMap<>(myColorsMap);
    newScheme.myVersion = myVersion;
  }

  public void clearColors(@NotNull Predicate<ColorKey> predicate) {
    Iterator<ColorKey> iterator = myColorsMap.keySet().iterator();
    while (iterator.hasNext()) {
      ColorKey key = iterator.next();
      if (predicate.test(key)) iterator.remove();
    }
  }

  public Map<ColorKey,Color> getColors(@NotNull Predicate<ColorKey> predicate) {
    Map<ColorKey,Color> colorMap = ContainerUtilRt.newHashMap();
    for (ColorKey key : myColorsMap.keySet()) {
      if (predicate.test(key)) {
        colorMap.put(key, myColorsMap.get(key));
      }
    }
    return colorMap;
  }

  @Override
  public void setEditorFontName(String fontName) {
    ModifiableFontPreferences currPreferences = ensureEditableFontPreferences();
    boolean useLigatures = currPreferences.useLigatures();
    int editorFontSize = getEditorFontSize();
    currPreferences.clear();
    currPreferences.register(fontName, editorFontSize);
    currPreferences.setUseLigatures(useLigatures);
    initFonts();
  }

  @Override
  public void setEditorFontSize(int fontSize) {
    fontSize = EditorFontsConstants.checkAndFixEditorFontSize(fontSize);
    ensureEditableFontPreferences().register(myFontPreferences.getFontFamily(), fontSize);
    initFonts();
  }

  @Override
  public void setUseAppFontPreferencesInEditor() {
    myFontPreferences = new DelegatingFontPreferences(()-> AppEditorFontOptions.getInstance().getFontPreferences());
    initFonts();
  }

  @Override
  public boolean isUseAppFontPreferencesInEditor() {
    return myFontPreferences instanceof DelegatingFontPreferences;
  }

  @Override
  public void setQuickDocFontSize(@NotNull FontSize fontSize) {
    if (myQuickDocFontSize != fontSize) {
      myQuickDocFontSize = fontSize;
      myIsSaveNeeded = true;
    }
  }

  @Override
  public void setLineSpacing(float lineSpacing) {
    ensureEditableFontPreferences().setLineSpacing(lineSpacing);
  }

  @NotNull
  @Override
  public Font getFont(EditorFontType key) {
    return myFontPreferences instanceof DelegatingFontPreferences ? EditorFontCache.getInstance().getFont(key) : super.getFont(key);
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
    preferences.copyTo(ensureEditableFontPreferences());
    initFonts();
  }

  @Override
  public String getEditorFontName() {
    return getFont(EditorFontType.PLAIN).getFamily();
  }

  @Override
  public int getEditorFontSize() {
    return myFontPreferences.getSize(myFontPreferences.getFontFamily());
  }

  @NotNull
  @Override
  public FontSize getQuickDocFontSize() {
    return myQuickDocFontSize;
  }

  @Override
  public float getLineSpacing() {
    return myFontPreferences.getLineSpacing();
  }

  protected void initFonts() {
    reset();
  }

  @Override
  protected EditorColorsScheme getFontCacheScheme() {
    return this;
  }

  public String toString() {
    return getName();
  }

  @Override
  public void readExternal(@NotNull Element parentNode) {
    UISettings settings = UISettings.getInstanceOrNull();
    ColorBlindness blindness = settings == null ? null : settings.getColorBlindness();
    myValueReader.setAttribute(blindness == null ? null : blindness.name());
    if (SCHEME_ELEMENT.equals(parentNode.getName())) {
      readScheme(parentNode);
    }
    else {
      List<Element> children = parentNode.getChildren(SCHEME_ELEMENT);
      if (children.isEmpty()) {
        throw new InvalidDataException("Scheme is not valid");
      }

      for (Element element : children) {
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
    boolean isDefault = isDefaultScheme != null && Boolean.parseBoolean(isDefaultScheme);
    if (!isDefault) {
      myParentScheme = getDefaultScheme(node.getAttributeValue(PARENT_SCHEME_ATTR, EmptyColorScheme.NAME));
    }

    myMetaInfo.clear();
    Ref<Float> fontScale = Ref.create();
    boolean clearEditorFonts = true;
    boolean clearConsoleFonts = true;
    for (Element childNode : node.getChildren()) {
      String childName = childNode.getName();
      switch (childName) {
        case OPTION_ELEMENT:
          readSettings(childNode, isDefault, fontScale);
          break;
        case EDITOR_FONT:
          readFontSettings(ensureEditableFontPreferences(), childNode, isDefault, fontScale.get(), clearEditorFonts);
          clearEditorFonts = false;
          break;
        case CONSOLE_FONT:
          readFontSettings(ensureEditableConsoleFontPreferences(), childNode, isDefault, fontScale.get(), clearConsoleFonts);
          clearConsoleFonts = false;
          break;
        case COLORS_ELEMENT:
          readColors(childNode);
          break;
        case ATTRIBUTES_ELEMENT:
          readAttributes(childNode);
          break;
        case META_INFO_ELEMENT:
          readMetaInfo(childNode);
          break;
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

  @NotNull
  private static EditorColorsScheme getDefaultScheme(@NotNull String name) {
    DefaultColorSchemesManager manager = DefaultColorSchemesManager.getInstance();
    EditorColorsScheme defaultScheme = manager.getScheme(name);
    if (defaultScheme == null) {
      defaultScheme = new TemporaryParent(name);
    }
    return defaultScheme;
  }


  private void readMetaInfo(@NotNull Element metaInfoElement) {
    myMetaInfo.clear();
    for (Element e: metaInfoElement.getChildren()) {
      if (PROPERTY_ELEMENT.equals(e.getName())) {
        String propertyName = e.getAttributeValue(PROPERTY_NAME_ATTR);
        if (propertyName != null) {
          myMetaInfo.setProperty(propertyName, e.getText());
        }
      }
    }
  }

  public void readAttributes(@NotNull Element childNode) {
    for (Element e : childNode.getChildren(OPTION_ELEMENT)) {
      Element valueElement = e.getChild(VALUE_ELEMENT);
      TextAttributesKey key = TextAttributesKey.find(e.getAttributeValue(NAME_ATTR));
      if (valueElement == null) {
        if (e.getAttributeValue(BASE_ATTRIBUTES_ATTR) != null) {
          myAttributesMap.put(key, USE_INHERITED_MARKER);
        }
        continue;
      }
      TextAttributes attr = myValueReader.read(TextAttributes.class, valueElement);
      if (attr != null) {
        myAttributesMap.put(key, attr);
        migrateErrorStripeColorFrom14(key, attr);
      }
    }
  }

  private void migrateErrorStripeColorFrom14(@NotNull TextAttributesKey name, @NotNull TextAttributes attr) {
    if (myVersion >= 141 || myParentScheme == null) return;

    Couple<Color> m = DEFAULT_STRIPE_COLORS.get(name.getExternalName());
    if (m != null && Comparing.equal(m.first, attr.getErrorStripeColor())) {
      attr.setErrorStripeColor(m.second);
    }
  }

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
    for (Element colorElement : childNode.getChildren(OPTION_ELEMENT)) {
      Color valueColor = myValueReader.read(Color.class, colorElement);
      final String colorName = colorElement.getAttributeValue(NAME_ATTR);
      if (BACKGROUND_COLOR_NAME.equals(colorName)) {
        // This setting has been deprecated to usages of HighlighterColors.TEXT attributes.
        myDeprecatedBackgroundColor = valueColor;
      }

      ColorKey name = ColorKey.find(colorName);
      myColorsMap.put(name, valueColor);
    }
  }

  private void readSettings(@NotNull Element childNode, boolean isDefault, @NotNull Ref<Float> fontScale) {
    switch (childNode.getAttributeValue(NAME_ATTR)) {
      case FONT_SCALE: {
        fontScale.set(myValueReader.read(Float.class, childNode));
        break;
      }
      case LINE_SPACING: {
        Float value = myValueReader.read(Float.class, childNode);
        if (value != null) setLineSpacing(value);
        break;
      }
      case EDITOR_FONT_SIZE: {
        int value = readFontSize(childNode, isDefault, fontScale.get());
        if (value > 0) setEditorFontSize(value);
        break;
      }
      case EDITOR_FONT_NAME: {
        String value = myValueReader.read(String.class, childNode);
        if (value != null) setEditorFontName(value);
        break;
      }
      case CONSOLE_LINE_SPACING: {
        Float value = myValueReader.read(Float.class, childNode);
        if (value != null) setConsoleLineSpacing(value);
        break;
      }
      case CONSOLE_FONT_SIZE: {
        int value = readFontSize(childNode, isDefault, fontScale.get());
        if (value > 0) setConsoleFontSize(value);
        break;
      }
      case CONSOLE_FONT_NAME: {
        String value = myValueReader.read(String.class, childNode);
        if (value != null) setConsoleFontName(value);
        break;
      }
      case EDITOR_QUICK_JAVADOC_FONT_SIZE: {
        FontSize value = myValueReader.read(FontSize.class, childNode);
        if (value != null) myQuickDocFontSize = value;
        break;
      }
      case EDITOR_LIGATURES: {
        Boolean value = myValueReader.read(Boolean.class, childNode);
        if (value != null) ensureEditableFontPreferences().setUseLigatures(value);
        break;
      }
      case CONSOLE_LIGATURES: {
        Boolean value = myValueReader.read(Boolean.class, childNode);
        if (value != null) {
          ensureEditableConsoleFontPreferences().setUseLigatures(value);
        }
        break;
      }
    }
  }

  private int readFontSize(Element element, boolean isDefault, Float fontScale) {
    if (isDefault) {
      return UISettings.getDefFontSize();
    }
    Integer intSize = myValueReader.read(Integer.class, element);
    if (intSize == null) {
      return -1;
    }
    return UISettings.restoreFontSize(intSize, fontScale);
  }

  private void readFontSettings(@NotNull ModifiableFontPreferences preferences,
                                @NotNull Element element,
                                boolean isDefaultScheme,
                                @Nullable Float fontScale,
                                boolean clearFonts) {
    if (clearFonts) preferences.clearFonts();
    List children = element.getChildren(OPTION_ELEMENT);
    String fontFamily = null;
    int size = -1;
    for (Object child : children) {
      Element e = (Element)child;
      if (EDITOR_FONT_NAME.equals(e.getAttributeValue(NAME_ATTR))) {
        fontFamily = myValueReader.read(String.class, e);
      }
      else if (EDITOR_FONT_SIZE.equals(e.getAttributeValue(NAME_ATTR))) {
        size = readFontSize(e, isDefaultScheme, fontScale);
      }
    }
    if (fontFamily != null && size > 1) {
      preferences.register(fontFamily, size);
    }
    else if (fontFamily != null) {
      preferences.addFontFamily(fontFamily);
    }
  }

  public void writeExternal(Element parentNode) {
    parentNode.setAttribute(NAME_ATTR, getName());
    parentNode.setAttribute(VERSION_ATTR, Integer.toString(myVersion));

    /*
     * FONT_SCALE is used to correctly identify the font size in both the JRE-managed HiDPI mode and
     * the IDE-managed HiDPI mode: {@link UIUtil#isJreHiDPIEnabled()}. Also, it helps to distinguish
     * the "hidpi-aware" scheme version from the previous one. Namely, the absence of the FONT_SCALE
     * attribute in the scheme indicates the previous "hidpi-unaware" scheme and the restored font size
     * is reset to default. It's assumed this (transition case) happens only once, after which the IDE
     * will be able to restore the font size according to its scale and the IDE HiDPI mode. The default
     * FONT_SCALE value should also be written by that reason.
     */
    if (!(myFontPreferences instanceof DelegatingFontPreferences) || !(myConsoleFontPreferences instanceof DelegatingFontPreferences)) {
      JdomKt.addOptionTag(parentNode, FONT_SCALE, String.valueOf(UISettings.getDefFontScale())); // must precede font options
    }

    if (myParentScheme != null && myParentScheme != EmptyColorScheme.INSTANCE) {
      parentNode.setAttribute(PARENT_SCHEME_ATTR, myParentScheme.getName());
    }
    
    if (!myMetaInfo.isEmpty()) {
      parentNode.addContent(metaInfoToElement());
    }

    if (getLineSpacing() != FontPreferences.DEFAULT_LINE_SPACING) {
      JdomKt.addOptionTag(parentNode, LINE_SPACING, String.valueOf(getLineSpacing()));
    }

    // IJ has used a 'single customizable font' mode for ages. That's why we want to support that format now, when it's possible
    // to specify fonts sequence (see getFontPreferences()), there are big chances that many clients still will use a single font.
    // That's why we want to use old format when zero or one font is selected and 'extended' format otherwise.
    boolean useOldFontFormat = myFontPreferences.getEffectiveFontFamilies().size() <= 1;
    if (!(myFontPreferences instanceof DelegatingFontPreferences)) {
      if (useOldFontFormat) {
        JdomKt.addOptionTag(parentNode, EDITOR_FONT_SIZE, String.valueOf(getEditorFontSize()));
        JdomKt.addOptionTag(parentNode, EDITOR_FONT_NAME, myFontPreferences.getFontFamily());
      }
      else {
        writeFontPreferences(EDITOR_FONT, parentNode, myFontPreferences);
      }
      writeLigaturesPreferences(parentNode, myFontPreferences, EDITOR_LIGATURES);
    }
    
    if (!(myConsoleFontPreferences instanceof DelegatingFontPreferences)) {
      if (myConsoleFontPreferences.getEffectiveFontFamilies().size() <= 1) {
        JdomKt.addOptionTag(parentNode, CONSOLE_FONT_NAME, getConsoleFontName());

        if (getConsoleFontSize() != getEditorFontSize()) {
          JdomKt.addOptionTag(parentNode, CONSOLE_FONT_SIZE, Integer.toString(getConsoleFontSize()));
        }
      }
      else {
        writeFontPreferences(CONSOLE_FONT, parentNode, myConsoleFontPreferences);
      }
      writeLigaturesPreferences(parentNode, myConsoleFontPreferences, CONSOLE_LIGATURES);
      if (getConsoleLineSpacing() != FontPreferences.DEFAULT_LINE_SPACING) {
        JdomKt.addOptionTag(parentNode, CONSOLE_LINE_SPACING, Float.toString(getConsoleLineSpacing()));
      }
    }

    if (DEFAULT_FONT_SIZE != getQuickDocFontSize()) {
      JdomKt.addOptionTag(parentNode, EDITOR_QUICK_JAVADOC_FONT_SIZE, getQuickDocFontSize().toString());
    }

    Element colorElements = new Element(COLORS_ELEMENT);
    Element attrElements = new Element(ATTRIBUTES_ELEMENT);

    writeColors(colorElements);
    writeAttributes(attrElements);

    if (!colorElements.getChildren().isEmpty()) {
      parentNode.addContent(colorElements);
    }
    if (!attrElements.getChildren().isEmpty()) {
      parentNode.addContent(attrElements);
    }
    
    myIsSaveNeeded = false;
  }

  private static void writeLigaturesPreferences(Element parentNode, FontPreferences preferences, String optionName) {
    if (preferences.useLigatures()) {
      JdomKt.addOptionTag(parentNode, optionName, String.valueOf(true));
    }
  }

  private static void writeFontPreferences(@NotNull String key, @NotNull Element parent, @NotNull FontPreferences preferences) {
    for (String fontFamily : preferences.getRealFontFamilies()) {
      Element element = new Element(key);
      JdomKt.addOptionTag(element, EDITOR_FONT_NAME, fontFamily);
      JdomKt.addOptionTag(element, EDITOR_FONT_SIZE, String.valueOf(preferences.getSize(fontFamily)));
      parent.addContent(element);
    }
  }

  private boolean isParentOverwritingInheritance(@NotNull TextAttributesKey key) {
    TextAttributes parentAttributes =
      myParentScheme instanceof AbstractColorsScheme ? ((AbstractColorsScheme)myParentScheme).getDirectlyDefinedAttributes(key) : null;
    return parentAttributes != null && parentAttributes != USE_INHERITED_MARKER;
  }

  private void writeAttributes(@NotNull Element attrElements) throws WriteExternalException {
    List<TextAttributesKey> list = new ArrayList<>(myAttributesMap.keySet());
    list.sort(null);
    for (TextAttributesKey key : list) {
      TextAttributes attributes = myAttributesMap.get(key);
      TextAttributesKey baseKey = key.getFallbackAttributeKey();
      if (attributes == USE_INHERITED_MARKER) {
        // do not store if  inheritance = on in the parent scheme (https://youtrack.jetbrains.com/issue/IDEA-162774)
        if (baseKey != null && isParentOverwritingInheritance(key)) {
          attrElements.addContent(new Element(OPTION_ELEMENT)
                                    .setAttribute(NAME_ATTR, key.getExternalName())
                                    .setAttribute(BASE_ATTRIBUTES_ATTR, baseKey.getExternalName()));
        }
        continue;
      }

      if (myParentScheme != null) {
        // fallback attributes must be not used, otherwise derived scheme as copy will not have such key
        TextAttributes parentAttributes = myParentScheme instanceof AbstractColorsScheme
                                          ? ((AbstractColorsScheme)myParentScheme).getDirectlyDefinedAttributes(key)
                                          : myParentScheme.getAttributes(key);
        if (parentAttributes != null && attributes.equals(parentAttributes)) {
          continue;
        }
      }

      Element valueElement = new Element(VALUE_ELEMENT);
      attributes.writeExternal(valueElement);
      attrElements.addContent(new Element(OPTION_ELEMENT).setAttribute(NAME_ATTR, key.getExternalName()).addContent(valueElement));
    }
  }

  public void optimizeAttributeMap() {
    EditorColorsScheme parentScheme = myParentScheme;
    if (parentScheme == null) {
      return;
    }

    for (TextAttributesKey key : new ArrayList<>(myAttributesMap.keySet())) {
      TextAttributes attributes = myAttributesMap.get(key);
      if (attributes == USE_INHERITED_MARKER) {
        if (key.getFallbackAttributeKey() == null) {
          myAttributesMap.remove(key);
        }
        continue;
      }

      TextAttributes parentAttributes = parentScheme instanceof DefaultColorsScheme
                                        ? ((DefaultColorsScheme)parentScheme).getAttributes(key, false)
                                        : parentScheme.getAttributes(key);
      if (Comparing.equal(parentAttributes, attributes)) {
        myAttributesMap.remove(key);
      }
    }
  }
  
  @NotNull
  private Element metaInfoToElement() {
    Element metaInfoElement = new Element(META_INFO_ELEMENT);
    myMetaInfo.setProperty(META_INFO_MODIFIED_TIME, META_INFO_DATE_FORMAT.format(new Date()));
    List<String> sortedPropertyNames = new ArrayList<>(myMetaInfo.stringPropertyNames());
    sortedPropertyNames.sort(null);
    for (String propertyName : sortedPropertyNames) {
      String value = myMetaInfo.getProperty(propertyName);
      Element propertyInfo = new Element(PROPERTY_ELEMENT);
      propertyInfo.setAttribute(PROPERTY_NAME_ATTR, propertyName);
      propertyInfo.setText(value);
      metaInfoElement.addContent(propertyInfo);
    }
    return metaInfoElement;
  }

  protected Color getOwnColor(ColorKey key) {
    return myColorsMap.get(key);
  }

  private void writeColors(Element colorElements) {
    List<ColorKey> list = new ArrayList<>(myColorsMap.keySet());
    list.sort(null);
    for (ColorKey key : list) {
      if (haveToWrite(key)) {
        Color value = myColorsMap.get(key);
        String value1 = value == null ? "" : Integer.toString(value.getRGB() & 0xFFFFFF, 16);
        JdomKt.addOptionTag(colorElements, key.getExternalName(), value1);
      }
    }
  }

  private boolean haveToWrite(@NotNull ColorKey key) {
    Color value = myColorsMap.get(key);
    if (myParentScheme != null) {
      if (myParentScheme instanceof AbstractColorsScheme) {
        if (Comparing.equal(((AbstractColorsScheme)myParentScheme).getOwnColor(key), value) && ((AbstractColorsScheme)myParentScheme).myColorsMap.containsKey(key)) {
          return false;
        }
      }
      else if (Comparing.equal((myParentScheme).getColor(key), value)) {
        return false;
      }
    }
    return true;
  }

  private ModifiableFontPreferences ensureEditableFontPreferences() {
    if (!(myFontPreferences instanceof ModifiableFontPreferences)) {
      ModifiableFontPreferences editablePrefs = new FontPreferencesImpl();
      myFontPreferences.copyTo(editablePrefs);
      myFontPreferences = editablePrefs;
      ((FontPreferencesImpl)myFontPreferences).setChangeListener(() -> initFonts());
    }
    return (ModifiableFontPreferences)myFontPreferences;
  }

  @NotNull
  @Override
  public FontPreferences getConsoleFontPreferences() {
    return myConsoleFontPreferences;
  }
  
  @Override
  public void setUseEditorFontPreferencesInConsole() {
    myConsoleFontPreferences = new DelegatingFontPreferences(() -> myFontPreferences);
    initFonts();
  }

  @Override
  public boolean isUseEditorFontPreferencesInConsole() {
    return myConsoleFontPreferences instanceof DelegatingFontPreferences;
  }

  @Override
  public void setConsoleFontPreferences(@NotNull FontPreferences preferences) {
    preferences.copyTo(ensureEditableConsoleFontPreferences());
    initFonts();
  }

  @Override
  public String getConsoleFontName() {
    return myConsoleFontPreferences.getFontFamily();
  }

  private ModifiableFontPreferences ensureEditableConsoleFontPreferences() {
    if (!(myConsoleFontPreferences instanceof ModifiableFontPreferences)) {
      ModifiableFontPreferences editablePrefs = new FontPreferencesImpl();
      myConsoleFontPreferences.copyTo(editablePrefs);
      myConsoleFontPreferences = editablePrefs;
    }
    return (ModifiableFontPreferences)myConsoleFontPreferences;
  }

  @Override
  public void setConsoleFontName(String fontName) {
    ModifiableFontPreferences consolePreferences = ensureEditableConsoleFontPreferences();
    int consoleFontSize = getConsoleFontSize();
    consolePreferences.clear();
    consolePreferences.register(fontName, consoleFontSize);
  }

  @Override
  public int getConsoleFontSize() {
    String font = getConsoleFontName();
    UISettings uiSettings = UISettings.getInstanceOrNull();
    if ((uiSettings == null || !uiSettings.getPresentationMode()) && myConsoleFontPreferences.hasSize(font)) {
      return myConsoleFontPreferences.getSize(font);
    }
    return getEditorFontSize();
  }

  @Override
  public void setConsoleFontSize(int fontSize) {
    ModifiableFontPreferences consoleFontPreferences = ensureEditableConsoleFontPreferences();
    fontSize = EditorFontsConstants.checkAndFixEditorFontSize(fontSize);
    consoleFontPreferences.register(getConsoleFontName(), fontSize);
    initFonts();
  }

  @Override
  public float getConsoleLineSpacing() {
    return myConsoleFontPreferences.getLineSpacing();
  }

  @Override
  public void setConsoleLineSpacing(float lineSpacing) {
    ensureEditableConsoleFontPreferences().setLineSpacing(lineSpacing);
  }

  protected TextAttributes getFallbackAttributes(@NotNull TextAttributesKey fallbackKey) {
    TextAttributes fallbackAttributes = getDirectlyDefinedAttributes(fallbackKey);
    TextAttributesKey fallbackKeyFallbackKey = fallbackKey.getFallbackAttributeKey();
    if (fallbackAttributes != null && (fallbackAttributes != USE_INHERITED_MARKER || fallbackKeyFallbackKey == null)) {
      return fallbackAttributes;
    }
    return fallbackKeyFallbackKey == null ? null : getFallbackAttributes(fallbackKeyFallbackKey);
  }

  /**
   * Looks for explicitly specified attributes either in the scheme or its parent scheme. No fallback keys are used.
   *
   * @param key The key to use for search.
   * @return Explicitly defined attribute or {@code null} if not found.
   */
  @Nullable
  public TextAttributes getDirectlyDefinedAttributes(@NotNull TextAttributesKey key) {
    TextAttributes attributes = myAttributesMap.get(key);
    if (attributes != null) {
      return attributes;
    }
    return myParentScheme instanceof AbstractColorsScheme ? ((AbstractColorsScheme)myParentScheme).getDirectlyDefinedAttributes(key) : null;
  }

  @NotNull
  @Override
  public SchemeState getSchemeState() {
    return myIsSaveNeeded ? SchemeState.POSSIBLY_CHANGED : SchemeState.UNCHANGED;
  }

  public void setSaveNeeded(boolean value) {
    myIsSaveNeeded = value;
  }
  
  public boolean isReadOnly() { return  false; }

  @NotNull
  @Override
  public Properties getMetaProperties() {
    return myMetaInfo;
  }
  
  public boolean canBeDeleted() {
    return myCanBeDeleted;
  }
  
  public void setCanBeDeleted(boolean canBeDeleted) {
    myCanBeDeleted = canBeDeleted;
  }
  
  public boolean isVisible() {
    return true;
  }

  public static boolean isVisible(@NotNull EditorColorsScheme scheme) {
    return !(scheme instanceof AbstractColorsScheme) || ((AbstractColorsScheme)scheme).isVisible();
  }

  @Nullable
  public AbstractColorsScheme getOriginal() {
    String originalSchemeName = getMetaProperties().getProperty(META_INFO_ORIGINAL);
    if (originalSchemeName != null) {
      EditorColorsScheme originalScheme = EditorColorsManager.getInstance().getScheme(originalSchemeName);
      if (originalScheme instanceof AbstractColorsScheme) return (AbstractColorsScheme)originalScheme;
    }
    return null;
  }

  public EditorColorsScheme getParentScheme() {
    return myParentScheme;
  }

  @NotNull
  @Override
  public Element writeScheme() {
    Element root = new Element("scheme");
    writeExternal(root);
    return root;
  }

  public boolean settingsEqual(Object other) {
    return settingsEqual(other, null);
  }

  public boolean settingsEqual(Object other, @Nullable Predicate<ColorKey> colorKeyFilter) {
    if (!(other instanceof AbstractColorsScheme)) return false;
    AbstractColorsScheme otherScheme = (AbstractColorsScheme)other;
    
    // parent is used only for default schemes (e.g. Darcula bundled in all ide (opposite to IDE-specific, like Cobalt))
    if (getBaseDefaultScheme(this) != getBaseDefaultScheme(otherScheme)) {
      return false;
    }

    for (String propertyName : myMetaInfo.stringPropertyNames()) {
      if (propertyName.equals(META_INFO_CREATION_TIME) ||
          propertyName.equals(META_INFO_MODIFIED_TIME) ||
          propertyName.equals(META_INFO_IDE) ||
          propertyName.equals(META_INFO_IDE_VERSION) ||
          propertyName.equals(META_INFO_ORIGINAL)
        ) {
        continue;
      }                                                                                                               

      if (!Comparing.equal(myMetaInfo.getProperty(propertyName), otherScheme.myMetaInfo.getProperty(propertyName))) {
        return false;
      }
    }

    return areDelegatingOrEqual(myFontPreferences, otherScheme.getFontPreferences()) &&
           areDelegatingOrEqual(myConsoleFontPreferences, otherScheme.getConsoleFontPreferences()) &&
           attributesEqual(otherScheme) &&
           colorsEqual(otherScheme, colorKeyFilter);
  }

  protected static boolean areDelegatingOrEqual(@NotNull FontPreferences preferences1, @NotNull FontPreferences preferences2) {
      boolean isDelegating1 = preferences1 instanceof DelegatingFontPreferences;
      boolean isDelegating2 = preferences2 instanceof DelegatingFontPreferences;
      return isDelegating1 || isDelegating2 ? isDelegating1 && isDelegating2 : preferences1.equals(preferences2);
    }

  protected boolean attributesEqual(AbstractColorsScheme otherScheme) {
    return myAttributesMap.equals(otherScheme.myAttributesMap);
  }

  protected boolean colorsEqual(AbstractColorsScheme otherScheme, @Nullable Predicate<ColorKey> colorKeyFilter) {
    return myColorsMap.equals(otherScheme.myColorsMap);
  }

  @Nullable
  private static EditorColorsScheme getBaseDefaultScheme(@NotNull EditorColorsScheme scheme) {
    if (!(scheme instanceof AbstractColorsScheme)) {
      return null;
    }
    if (scheme instanceof DefaultColorsScheme) {
      return scheme;
    }
    EditorColorsScheme parent = ((AbstractColorsScheme)scheme).myParentScheme;
    return parent != null ? getBaseDefaultScheme(parent) : null;
  }
  
  private static class TemporaryParent extends EditorColorsSchemeImpl {

    private static Logger LOG = Logger.getInstance(TemporaryParent.class);
    
    private String myParentName;
    private boolean isErrorReported;

    public TemporaryParent(@NotNull String parentName) {
      super(EmptyColorScheme.INSTANCE);
      myParentName = parentName;
    }

    public String getParentName() {
      return myParentName;
    }

    @Override
    public TextAttributes getAttributes(@Nullable TextAttributesKey key) {
      reportError();
      return super.getAttributes(key);
    }

    @Nullable
    @Override
    public Color getColor(ColorKey key) {
      reportError();
      return super.getColor(key);
    }

    private void reportError() {
      if (!isErrorReported) {
        LOG.error("Unresolved link to " + myParentName);
        isErrorReported = true;
      }
    }
  }

  public void setParent(@NotNull EditorColorsScheme newParent) {
    assert newParent instanceof ReadOnlyColorsScheme : "New parent scheme must be read-only";
    myParentScheme = newParent;
  }

  void resolveParent(@NotNull Function<String,EditorColorsScheme> nameResolver) {
    if (myParentScheme instanceof TemporaryParent) {
      String parentName = ((TemporaryParent)myParentScheme).getParentName();
      EditorColorsScheme newParent = nameResolver.apply(parentName);
      if (newParent == null || !(newParent instanceof ReadOnlyColorsScheme)) {
        throw new InvalidDataException(parentName);
      }
      myParentScheme = newParent;
    }
  }
}
