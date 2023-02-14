// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.BundleBase;
import com.intellij.DynamicBundle;
import com.intellij.application.options.EditorFontsConstants;
import com.intellij.configurationStore.SerializableScheme;
import com.intellij.ide.ui.ColorBlindness;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemeState;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.JBColor;
import com.intellij.util.JdomKt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformUtils;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings("UseJBColor")
public abstract class AbstractColorsScheme extends EditorFontCacheImpl implements EditorColorsScheme, SerializableScheme {
  public static final TextAttributes INHERITED_ATTRS_MARKER = new TextAttributes();
  public static final Color INHERITED_COLOR_MARKER = JBColor.marker("INHERITED_COLOR_MARKER");
  public static final Color NULL_COLOR_MARKER = JBColor.marker("NULL_COLOR_MARKER");

  public static final int CURR_VERSION = 142;

  public static final String NAME_BUNDLE_PROPERTY = "lcNameBundle";
  public static final String NAME_KEY_PROPERTY = "lcNameKey";

  protected EditorColorsScheme myParentScheme;

  private @NotNull FontPreferences myFontPreferences = new DelegatingFontPreferences(() -> AppEditorFontOptions.getInstance().getFontPreferences());
  private @NotNull FontPreferences myConsoleFontPreferences = new DelegatingFontPreferences(() -> myFontPreferences);

  private final ValueElementReader myValueReader = new TextAttributesReader();
  private String mySchemeName;

  private boolean myIsSaveNeeded;

  private boolean myCanBeDeleted = true;

  // version influences XML format and triggers migration
  private int myVersion = CURR_VERSION;

  Map<ColorKey, Color> myColorsMap = new HashMap<>();
  Map<@NonNls String, TextAttributes> myAttributesMap = new HashMap<>();

  private static final @NonNls String EDITOR_FONT       = "font";
  private static final @NonNls String CONSOLE_FONT      = "console-font";
  private static final @NonNls String EDITOR_FONT_NAME  = "EDITOR_FONT_NAME";
  private static final @NonNls String CONSOLE_FONT_NAME = "CONSOLE_FONT_NAME";
  private                      Color  myDeprecatedBackgroundColor    = null;
  private static final @NonNls String SCHEME_ELEMENT                 = "scheme";
  public static final @NonNls String NAME_ATTR                      = "name";
  private static final @NonNls String VERSION_ATTR                   = "version";
  private static final @NonNls String BASE_ATTRIBUTES_ATTR           = "baseAttributes";
  private static final @NonNls String DEFAULT_SCHEME_ATTR            = "default_scheme";
  private static final @NonNls String PARENT_SCHEME_ATTR             = "parent_scheme";
  private static final @NonNls String OPTION_ELEMENT                 = "option";
  private static final @NonNls String COLORS_ELEMENT                 = "colors";
  private static final @NonNls String ATTRIBUTES_ELEMENT             = "attributes";
  private static final @NonNls String VALUE_ELEMENT                  = "value";
  private static final @NonNls String BACKGROUND_COLOR_NAME          = "BACKGROUND";
  private static final @NonNls String LINE_SPACING                   = "LINE_SPACING";
  private static final @NonNls String CONSOLE_LINE_SPACING           = "CONSOLE_LINE_SPACING";
  private static final @NonNls String FONT_SCALE                     = "FONT_SCALE";
  private static final @NonNls String EDITOR_FONT_SIZE               = "EDITOR_FONT_SIZE";
  private static final @NonNls String CONSOLE_FONT_SIZE              = "CONSOLE_FONT_SIZE";
  private static final @NonNls String EDITOR_LIGATURES               = "EDITOR_LIGATURES";
  private static final @NonNls String CONSOLE_LIGATURES              = "CONSOLE_LIGATURES";


  //region Meta info-related fields
  private final Properties myMetaInfo = new Properties();
  private static final SimpleDateFormat META_INFO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

  private static final @NonNls String META_INFO_ELEMENT       = "metaInfo";
  private static final @NonNls String PROPERTY_ELEMENT        = "property";
  private static final @NonNls String PROPERTY_NAME_ATTR      = "name";

  private static final @NonNls String META_INFO_CREATION_TIME = "created";
  private static final @NonNls String META_INFO_MODIFIED_TIME = "modified";
  private static final @NonNls String META_INFO_IDE           = "ide";
  private static final @NonNls String META_INFO_IDE_VERSION   = "ideVersion";
  private static final @NonNls String META_INFO_ORIGINAL      = "originalScheme";

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

  @Override
  public @NotNull Color getDefaultBackground() {
    final Color c = getAttributes(HighlighterColors.TEXT).getBackgroundColor();
    return c != null ? c : Color.white;
  }

  @Override
  public @NotNull Color getDefaultForeground() {
    final Color c = getAttributes(HighlighterColors.TEXT).getForegroundColor();
    return c != null ? c : Color.black;
  }

  @Override
  public @NotNull String getName() {
    return mySchemeName;
  }

  @Override
  public @NotNull String getDisplayName() {
    if (!(this instanceof ReadOnlyColorsScheme)) {
      EditorColorsScheme original = getOriginal();
      if (original instanceof ReadOnlyColorsScheme) {
        return original.getDisplayName();
      }
    }
    String baseName = Scheme.getBaseName(getName()); //NON-NLS
    return ObjectUtils.chooseNotNull(getLocalizedName(), baseName);
  }

  protected @Nullable @Nls String getLocalizedName() {
    String bundlePath = getMetaProperties().getProperty(NAME_BUNDLE_PROPERTY);
    String bundleKey = getMetaProperties().getProperty(NAME_KEY_PROPERTY);
    if (bundlePath != null && bundleKey != null) {
      ResourceBundle bundle = DynamicBundle.getResourceBundle(getClass().getClassLoader(), bundlePath);
      return BundleBase.messageOrDefault(bundle, bundleKey, null);
    }
    return null;
  }

  @Override
  public abstract Object clone();

  public void copyTo(AbstractColorsScheme newScheme) {
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

    newScheme.myAttributesMap = new HashMap<>(myAttributesMap);
    newScheme.myColorsMap = new HashMap<>(myColorsMap);
    newScheme.myVersion = myVersion;
  }

  public @NotNull Set<ColorKey> getColorKeys() {
    return myColorsMap.keySet();
  }

  /**
   * Returns attributes defined in this scheme (not inherited from a parent).
   */
  public @NotNull Map<@NonNls String, TextAttributes> getDirectlyDefinedAttributes() {
    return new HashMap<>(myAttributesMap);
  }

  /**
   * Returns colors defined in this scheme (not inherited from a parent).
   */
  public @NotNull Map<ColorKey, Color> getDirectlyDefinedColors() {
    return new HashMap<>(myColorsMap);
  }

  @Override
  public void setEditorFontName(String fontName) {
    ModifiableFontPreferences currPreferences = ensureEditableFontPreferences();
    boolean useLigatures = currPreferences.useLigatures();
    float editorFontSize = getEditorFontSize2D();
    currPreferences.clear();
    currPreferences.register(fontName, editorFontSize);
    currPreferences.setUseLigatures(useLigatures);
    initFonts();
  }

  @Override
  public void setEditorFontSize(int fontSize) {
    setEditorFontSize((float)fontSize);
  }

  @Override
  public void setEditorFontSize(float fontSize) {
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
  public void setLineSpacing(float lineSpacing) {
    ensureEditableFontPreferences().setLineSpacing(lineSpacing);
  }

  @Override
  public boolean isUseLigatures() {
    return getFontPreferences().useLigatures();
  }

  @Override
  public void setUseLigatures(boolean useLigatures) {
    ensureEditableFontPreferences().setUseLigatures(useLigatures);
  }

  @Override
  public @NotNull Font getFont(EditorFontType key) {
    return myFontPreferences instanceof DelegatingFontPreferences ? EditorFontCache.getInstance().getFont(key) : super.getFont(key);
  }

  @Override
  public void setName(@NotNull String name) {
    mySchemeName = name;
  }

  @Override
  public @NotNull FontPreferences getFontPreferences() {
    return myFontPreferences;
  }

  @Override
  public void setFontPreferences(@NotNull FontPreferences preferences) {
    preferences.copyTo(ensureEditableFontPreferences());
    initFonts();
  }

  @Override
  public @NlsSafe String getEditorFontName() {
    return AppFontOptions.NEW_FONT_SELECTOR ? myFontPreferences.getFontFamily() : getFont(EditorFontType.PLAIN).getFamily();
  }

  @Override
  public int getEditorFontSize() {
    return myFontPreferences.getSize(myFontPreferences.getFontFamily());
  }

  @Override
  public float getEditorFontSize2D() {
    return myFontPreferences.getSize2D(myFontPreferences.getFontFamily());
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

  public @NonNls String toString() {
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
        case OPTION_ELEMENT -> readSettings(childNode, isDefault, fontScale);
        case EDITOR_FONT -> {
          readFontSettings(ensureEditableFontPreferences(), childNode, isDefault, fontScale.get(), clearEditorFonts);
          clearEditorFonts = false;
        }
        case CONSOLE_FONT -> {
          readFontSettings(ensureEditableConsoleFontPreferences(), childNode, isDefault, fontScale.get(), clearConsoleFonts);
          clearConsoleFonts = false;
        }
        case COLORS_ELEMENT -> readColors(childNode);
        case ATTRIBUTES_ELEMENT -> readAttributes(childNode);
        case META_INFO_ELEMENT -> readMetaInfo(childNode);
      }
    }

    if (myDeprecatedBackgroundColor != null) {
      TextAttributes textAttributes = myAttributesMap.get(HighlighterColors.TEXT.getExternalName());
      if (textAttributes == null) {
        textAttributes = new TextAttributes(Color.black, myDeprecatedBackgroundColor, null, EffectType.BOXED, Font.PLAIN);
        myAttributesMap.put(HighlighterColors.TEXT.getExternalName(), textAttributes);
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

  private static @NotNull EditorColorsScheme getDefaultScheme(@NotNull String name) {
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
      String keyName = e.getAttributeValue(NAME_ATTR);
      Element valueElement = e.getChild(VALUE_ELEMENT);
      TextAttributes attr = valueElement != null ? myValueReader.read(TextAttributes.class, valueElement) :
                            e.getAttributeValue(BASE_ATTRIBUTES_ATTR) != null ? INHERITED_ATTRS_MARKER :
                            null;
      if (attr != null) {
        myAttributesMap.put(keyName, attr);
      }
    }
  }

  public void readColors(@NotNull Element childNode) {
    for (Element colorElement : childNode.getChildren(OPTION_ELEMENT)) {
      String keyName = colorElement.getAttributeValue(NAME_ATTR);
      Color valueColor = myValueReader.read(Color.class, colorElement);
      if (valueColor == null && colorElement.getAttributeValue(BASE_ATTRIBUTES_ATTR) != null) {
        valueColor = INHERITED_COLOR_MARKER;
      }
      if (BACKGROUND_COLOR_NAME.equals(keyName)) {
        // This setting has been deprecated to usages of HighlighterColors.TEXT attributes.
        myDeprecatedBackgroundColor = valueColor;
      }
      ColorKey name = ColorKey.find(keyName);
      myColorsMap.put(name, valueColor == null ? NULL_COLOR_MARKER : valueColor);
    }
  }

  private void readSettings(@NotNull Element childNode, boolean isDefault, @NotNull Ref<Float> fontScale) {
    switch (childNode.getAttributeValue(NAME_ATTR)) {
      case FONT_SCALE -> fontScale.set(myValueReader.read(Float.class, childNode));
      case LINE_SPACING -> {
        Float value = myValueReader.read(Float.class, childNode);
        if (value != null) setLineSpacing(value);
      }
      case EDITOR_FONT_SIZE -> {
        float value = readFontSize(childNode, isDefault, fontScale.get());
        if (value > 0) setEditorFontSize(value);
      }
      case EDITOR_FONT_NAME -> {
        String value = myValueReader.read(String.class, childNode);
        if (value != null) setEditorFontName(value);
      }
      case CONSOLE_LINE_SPACING -> {
        Float value = myValueReader.read(Float.class, childNode);
        if (value != null) setConsoleLineSpacing(value);
      }
      case CONSOLE_FONT_SIZE -> {
        float value = readFontSize(childNode, isDefault, fontScale.get());
        if (value > 0) setConsoleFontSize(value);
      }
      case CONSOLE_FONT_NAME -> {
        String value = myValueReader.read(String.class, childNode);
        if (value != null) setConsoleFontName(value);
      }
      case EDITOR_LIGATURES -> {
        Boolean value = myValueReader.read(Boolean.class, childNode);
        if (value != null) ensureEditableFontPreferences().setUseLigatures(value);
      }
      case CONSOLE_LIGATURES -> {
        Boolean value = myValueReader.read(Boolean.class, childNode);
        if (value != null) {
          ensureEditableConsoleFontPreferences().setUseLigatures(value);
        }
      }
    }
  }

  private float readFontSize(Element element, boolean isDefault, Float fontScale) {
    if (isDefault) {
      return UISettings.getDefFontSize();
    }
    // Use integer size for compatibility
    Integer intSize = myValueReader.read(Integer.class, element);
    if (intSize == null) {
      return -1;
    }
    return UISettings.restoreFontSize(intSize.floatValue(), fontScale);
  }

  private void readFontSettings(@NotNull ModifiableFontPreferences preferences,
                                @NotNull Element element,
                                boolean isDefaultScheme,
                                @Nullable Float fontScale,
                                boolean clearFonts) {
    if (clearFonts) preferences.clearFonts();
    List<Element> children = element.getChildren(OPTION_ELEMENT);
    String fontFamily = null;
    float size = -1;
    for (Element e : children) {
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

    // IJ has used a 'single customizable font' mode for ages. That's why we want to support that format now, when it's possible
    // to specify fonts sequence (see getFontPreferences()), there are big chances that many clients still will use a single font.
    // That's why we want to use old format when zero or one font is selected and 'extended' format otherwise.
    boolean useOldFontFormat = myFontPreferences.getEffectiveFontFamilies().size() <= 1;
    if (!(myFontPreferences instanceof DelegatingFontPreferences)) {
      JdomKt.addOptionTag(parentNode, LINE_SPACING, String.valueOf(getLineSpacing()));
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
          // Write font size as integer for compatibility
          JdomKt.addOptionTag(parentNode, CONSOLE_FONT_SIZE, Integer.toString(getConsoleFontSize()));
        }
      }
      else {
        writeFontPreferences(CONSOLE_FONT, parentNode, myConsoleFontPreferences);
      }
      writeLigaturesPreferences(parentNode, myConsoleFontPreferences, CONSOLE_LIGATURES);
      if ((myFontPreferences instanceof DelegatingFontPreferences) || getConsoleLineSpacing() != getLineSpacing()) {
        JdomKt.addOptionTag(parentNode, CONSOLE_LINE_SPACING, Float.toString(getConsoleLineSpacing()));
      }
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
      // Write font size as integer for compatibility
      JdomKt.addOptionTag(element, EDITOR_FONT_SIZE, String.valueOf(preferences.getSize(fontFamily)));
      parent.addContent(element);
    }
  }

  private void writeAttributes(@NotNull Element attrElements) {
    List<Map.Entry<String, TextAttributes>> list = new ArrayList<>(myAttributesMap.entrySet());
    list.sort(Map.Entry.comparingByKey());
    for (Map.Entry<String, TextAttributes> entry : list) {
      String keyName = entry.getKey();
      TextAttributes attributes = entry.getValue();
      writeAttribute(attrElements, TextAttributesKey.find(keyName), attributes);
    }
  }

  private void writeAttribute(@NotNull Element attrElements,
                              @NotNull TextAttributesKey key,
                              @NotNull TextAttributes attributes) {
    if (attributes == INHERITED_ATTRS_MARKER) {
      TextAttributesKey baseKey = key.getFallbackAttributeKey();
      // IDEA-162774 do not store if  inheritance = on in the parent scheme
      TextAttributes parentAttributes = myParentScheme instanceof AbstractColorsScheme ?
                                        ((AbstractColorsScheme)myParentScheme).getDirectlyDefinedAttributes(key) : null;
      boolean parentOverwritingInheritance = parentAttributes != null && parentAttributes != INHERITED_ATTRS_MARKER;
      if (parentOverwritingInheritance) {
        attrElements.addContent(new Element(OPTION_ELEMENT)
                                  .setAttribute(NAME_ATTR, key.getExternalName())
                                  .setAttribute(BASE_ATTRIBUTES_ATTR, baseKey != null ? baseKey.getExternalName() : ""));
      }
      return;
    }

    if (myParentScheme != null) {
      // fallback attributes must be not used, otherwise derived scheme as copy will not have such key
      TextAttributes parentAttributes = myParentScheme instanceof AbstractColorsScheme
                                        ? ((AbstractColorsScheme)myParentScheme).getDirectlyDefinedAttributes(key)
                                        : myParentScheme.getAttributes(key);
      if (attributes.equals(parentAttributes)) {
        return;
      }
    }

    Element valueElement = new Element(VALUE_ELEMENT);
    attributes.writeExternal(valueElement);
    attrElements.addContent(new Element(OPTION_ELEMENT).setAttribute(NAME_ATTR, key.getExternalName()).addContent(valueElement));
  }

  public void optimizeAttributeMap() {
    EditorColorsScheme parentScheme = myParentScheme;
    if (parentScheme == null) {
      return;
    }
    myAttributesMap.entrySet().removeIf(entry -> {
      String keyName = entry.getKey();
      TextAttributes attrs = entry.getValue();
      TextAttributesKey key = TextAttributesKey.find(keyName);
      if (attrs == INHERITED_ATTRS_MARKER) {
        return !hasExplicitlyDefinedAttributes(parentScheme, key);
      }
      TextAttributes parent = parentScheme instanceof DefaultColorsScheme ?
                              ((DefaultColorsScheme)parentScheme).getAttributes(key, false) : parentScheme.getAttributes(key);
      return Comparing.equal(parent, attrs);
    });

    myColorsMap.keySet().removeAll(myColorsMap.keySet().stream().filter(key -> {
      Color color = myColorsMap.get(key);
      if (color == INHERITED_COLOR_MARKER) {
        return !hasExplicitlyDefinedColors(parentScheme, key);
      }

      Color parent = parentScheme instanceof DefaultColorsScheme
                     ? ((DefaultColorsScheme)parentScheme).getColor(key, false)
                     : parentScheme.getColor(key);
      return Comparing.equal(parent, color == NULL_COLOR_MARKER ? null : color);
    }).collect(Collectors.toSet()));
  }

  private static boolean hasExplicitlyDefinedAttributes(@NotNull EditorColorsScheme scheme, @NotNull TextAttributesKey key) {
    TextAttributes directAttrs =
      scheme instanceof DefaultColorsScheme ? ((DefaultColorsScheme)scheme).getDirectlyDefinedAttributes(key) : null;
    return directAttrs != null && directAttrs != INHERITED_ATTRS_MARKER;
  }

  private static boolean hasExplicitlyDefinedColors(@NotNull EditorColorsScheme scheme, @NotNull ColorKey key) {
    Color directColor =
      scheme instanceof DefaultColorsScheme ? ((DefaultColorsScheme)scheme).getDirectlyDefinedColor(key) : null;
    return directColor != null && directColor != INHERITED_COLOR_MARKER;
  }

  private @NotNull Element metaInfoToElement() {
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

  private void writeColors(Element colorElements) {
    List<ColorKey> list = new ArrayList<>(myColorsMap.keySet());
    list.sort(null);
    for (ColorKey key : list) {
      writeColor(colorElements, key);
    }
  }

  private void writeColor(@NotNull Element colorElements, @NotNull ColorKey key) {
    Color color = myColorsMap.get(key);
    if (color == INHERITED_COLOR_MARKER) {
      ColorKey fallbackKey = key.getFallbackColorKey();
      Color parentFallback = myParentScheme instanceof AbstractColorsScheme ?
                             ((AbstractColorsScheme)myParentScheme).getDirectlyDefinedColor(key) : null;
      boolean parentOverwritingInheritance = parentFallback != null && parentFallback != INHERITED_COLOR_MARKER;
      if (fallbackKey != null && parentOverwritingInheritance) {
        colorElements.addContent(new Element(OPTION_ELEMENT)
                                  .setAttribute(NAME_ATTR, key.getExternalName())
                                  .setAttribute(BASE_ATTRIBUTES_ATTR, fallbackKey.getExternalName()));
      }
      return;
    }

    if (myParentScheme != null) {
      Color parent = myParentScheme instanceof AbstractColorsScheme
                     ? ((AbstractColorsScheme)myParentScheme).getDirectlyDefinedColor(key)
                     : myParentScheme.getColor(key);
      if (parent != null && colorsEqual(color, parent)) {
        return;
      }
    }

    String rgb = "";
    if (color != NULL_COLOR_MARKER) {
      rgb = Integer.toString(0xFFFFFF & color.getRGB(), 16);
      int alpha = 0xFF & color.getAlpha();
      if (alpha != 0xFF) rgb += Integer.toString(alpha, 16);
    }
    JdomKt.addOptionTag(colorElements, key.getExternalName(), rgb);
  }

  protected static boolean colorsEqual(@Nullable Color c1, @Nullable Color c2) {
    if (c1 == NULL_COLOR_MARKER) return c1 == c2;
    return Comparing.equal(c1, c2 == NULL_COLOR_MARKER ? null : c2);
  }

  private ModifiableFontPreferences ensureEditableFontPreferences() {
    if (!(myFontPreferences instanceof ModifiableFontPreferences)) {
      ModifiableFontPreferences editablePrefs = new FontPreferencesImpl();
      myFontPreferences.copyTo(editablePrefs);
      myFontPreferences = editablePrefs;
      ((FontPreferencesImpl)myFontPreferences).addChangeListener((source) -> initFonts());
    }
    return (ModifiableFontPreferences)myFontPreferences;
  }

  @Override
  public @NotNull FontPreferences getConsoleFontPreferences() {
    if (!AppConsoleFontOptions.getInstance().isUseEditorFont()) {
      return AppConsoleFontOptions.getInstance().getFontPreferences();
    }
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
  public @NotNull @NlsSafe String getConsoleFontName() {
    return getConsoleFontPreferences().getFontFamily();
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
    float consoleFontSize = getConsoleFontSize2D();
    consolePreferences.clear();
    consolePreferences.register(fontName, consoleFontSize);
  }

  @Override
  public int getConsoleFontSize() {
    return (int)(getConsoleFontSize2D() + 0.5);
  }

  @Override
  public float getConsoleFontSize2D() {
    String font = getConsoleFontName();
    UISettings uiSettings = UISettings.getInstanceOrNull();
    if ((uiSettings == null || !uiSettings.getPresentationMode()) && getConsoleFontPreferences().hasSize(font)) {
      return getConsoleFontPreferences().getSize2D(font);
    }
    return getEditorFontSize2D();
  }

  @Override
  public void setConsoleFontSize(int fontSize) {
    setConsoleFontSize((float)fontSize);
  }

  @Override
  public void setConsoleFontSize(float fontSize) {
    ModifiableFontPreferences consoleFontPreferences = ensureEditableConsoleFontPreferences();
    fontSize = EditorFontsConstants.checkAndFixEditorFontSize(fontSize);
    consoleFontPreferences.register(getConsoleFontName(), fontSize);
    initFonts();
  }

  @Override
  public float getConsoleLineSpacing() {
    return getConsoleFontPreferences().getLineSpacing();
  }

  @Override
  public void setConsoleLineSpacing(float lineSpacing) {
    ensureEditableConsoleFontPreferences().setLineSpacing(lineSpacing);
  }

  protected @Nullable TextAttributes getFallbackAttributes(@NotNull TextAttributesKey fallbackKey) {
    TextAttributesKey cur = fallbackKey;
    while (true) {
      TextAttributes attrs = getDirectlyDefinedAttributes(cur);
      TextAttributesKey next = cur.getFallbackAttributeKey();
      if (attrs != null && (attrs != INHERITED_ATTRS_MARKER || next == null)) {
        return attrs;
      }
      if (next == null) return null;
      cur = next;
    }
  }

  protected @Nullable Color getFallbackColor(@NotNull ColorKey fallbackKey) {
    ColorKey cur = fallbackKey;
    while (true) {
      Color color = getDirectlyDefinedColor(cur);
      if (color == NULL_COLOR_MARKER) return null;
      ColorKey next = cur.getFallbackColorKey();
      if (color != null && (color != INHERITED_COLOR_MARKER || next == null)) {
        return color;
      }
      if (next == null) return null;
      cur = next;
    }
  }

  /**
   * Looks for explicitly specified attributes either in the scheme or its parent scheme. No fallback keys are used.
   *
   * @param key The key to use for search.
   * @return Explicitly defined attribute or {@code null} if not found.
   */
  public @Nullable TextAttributes getDirectlyDefinedAttributes(@NotNull TextAttributesKey key) {
    TextAttributes attributes = myAttributesMap.get(key.getExternalName());
    return attributes != null ? attributes :
           myParentScheme instanceof AbstractColorsScheme ? ((AbstractColorsScheme)myParentScheme).getDirectlyDefinedAttributes(key) :
           null;
  }

  /**
   * Looks for explicitly specified color either in the scheme or its parent scheme. No fallback keys are used.
   *
   * @param key The key to use for search.
   * @return Explicitly defined color or {@code null} if not found.
   */
  public @Nullable Color getDirectlyDefinedColor(@NotNull ColorKey key) {
    Color color = myColorsMap.get(key);
    return color != null ? color :
           myParentScheme instanceof AbstractColorsScheme ? ((AbstractColorsScheme)myParentScheme).getDirectlyDefinedColor(key) :
           null;
  }

  @Override
  public @NotNull SchemeState getSchemeState() {
    return myIsSaveNeeded ? SchemeState.POSSIBLY_CHANGED : SchemeState.UNCHANGED;
  }

  public void setSaveNeeded(boolean value) {
    myIsSaveNeeded = value;
  }

  public boolean isReadOnly() { return  false; }

  @Override
  public @NotNull Properties getMetaProperties() {
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

  public @Nullable AbstractColorsScheme getOriginal() {
    String originalSchemeName = getMetaProperties().getProperty(META_INFO_ORIGINAL);
    if (originalSchemeName != null) {
      EditorColorsScheme originalScheme = EditorColorsManager.getInstance().getScheme(originalSchemeName);
      if (originalScheme instanceof AbstractColorsScheme && originalScheme != this) return (AbstractColorsScheme)originalScheme;
    }
    return null;
  }

  public EditorColorsScheme getParentScheme() {
    return myParentScheme;
  }

  @Override
  public @NotNull Element writeScheme() {
    Element root = new Element("scheme");
    writeExternal(root);
    return root;
  }

  public boolean settingsEqual(Object other) {
    return settingsEqual(other, null);
  }

  public boolean settingsEqual(Object other, @Nullable Predicate<? super ColorKey> colorKeyFilter) {
    if (!(other instanceof AbstractColorsScheme otherScheme)) return false;

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

      if (!Objects.equals(myMetaInfo.getProperty(propertyName), otherScheme.myMetaInfo.getProperty(propertyName))) {
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

  protected boolean colorsEqual(AbstractColorsScheme otherScheme, @Nullable Predicate<? super ColorKey> colorKeyFilter) {
    if (myColorsMap.size() != otherScheme.myColorsMap.size()) return false;
    for (Map.Entry<ColorKey, Color> entry : myColorsMap.entrySet()) {
      Color c1 = entry.getValue();
      ColorKey key = entry.getKey();
      Color c2 = otherScheme.myColorsMap.get(key);
      if (!colorsEqual(c1, c2)) return false;
    }
    return true;
  }

  private static @Nullable EditorColorsScheme getBaseDefaultScheme(@NotNull EditorColorsScheme scheme) {
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

    private final String myParentName;

    TemporaryParent(@NotNull String parentName) {
      super(EmptyColorScheme.INSTANCE);
      myParentName = parentName;
    }

    public @NonNls String getParentName() {
      return myParentName;
    }
  }

  public void setParent(@NotNull EditorColorsScheme newParent) {
    assert newParent instanceof ReadOnlyColorsScheme : "New parent scheme must be read-only";
    myParentScheme = newParent;
  }

  void resolveParent(@NotNull Function<? super String, ? extends EditorColorsScheme> nameResolver) {
    if (myParentScheme instanceof TemporaryParent) {
      String parentName = ((TemporaryParent)myParentScheme).getParentName();
      EditorColorsScheme newParent = nameResolver.apply(parentName);
      if (!(newParent instanceof ReadOnlyColorsScheme)) {
        throw new InvalidDataException(parentName);
      }
      myParentScheme = newParent;
    }
  }
}
