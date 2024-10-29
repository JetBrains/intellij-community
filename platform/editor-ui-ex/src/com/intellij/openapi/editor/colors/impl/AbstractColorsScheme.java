// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.BundleBase;
import com.intellij.DynamicBundle;
import com.intellij.application.options.EditorFontsConstants;
import com.intellij.configurationStore.SerializableScheme;
import com.intellij.ide.ui.ColorBlindness;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.util.PlatformUtils;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings("UseJBColor")
public abstract class AbstractColorsScheme extends EditorFontCacheImpl implements EditorColorsScheme, SerializableScheme {
  private static final Logger LOG = Logger.getInstance(AbstractColorsScheme.class);

  public static final TextAttributes INHERITED_ATTRS_MARKER = new TextAttributes();
  public static final Color INHERITED_COLOR_MARKER = JBColor.marker("INHERITED_COLOR_MARKER");
  public static final Color NULL_COLOR_MARKER = JBColor.marker("NULL_COLOR_MARKER");

  public static final int CURR_VERSION = 142;

  public static final String NAME_BUNDLE_PROPERTY = "lcNameBundle";
  public static final String NAME_KEY_PROPERTY = "lcNameKey";
  public static final @NonNls String NAME_ATTR = "name";
  public static final @NonNls String META_INFO_PLUGIN_ID = "pluginId";
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
  /**
   * Obsolete: removed since 24.1
   */
  private static final @NonNls String META_INFO_CREATION_TIME = "created";
  /**
   * Obsolete: removed since 24.1
   */
  private static final @NonNls String META_INFO_MODIFIED_TIME = "modified";
  private static final @NonNls String META_INFO_IDE = "ide";
  private static final @NonNls String META_INFO_IDE_VERSION = "ideVersion";
  public static final @NonNls String META_INFO_ORIGINAL = "originalScheme";
  private static final @NonNls String META_INFO_PARTIAL = "partialSave";
  private final ValueElementReader myValueReader = new TextAttributesReader();
  //region Meta info-related fields
  private final Properties metaInfo = new Properties();
  protected EditorColorsScheme parentScheme;
  Map<ColorKey, Color> colorMap = new HashMap<>();
  Map<@NonNls String, TextAttributes> attributesMap = new HashMap<>();
  private @NotNull FontPreferences fontPreferences =
    new DelegatingFontPreferences(() -> AppEditorFontOptions.getInstance().getFontPreferences());
  private @NotNull FontPreferences consoleFontPreferences = new DelegatingFontPreferences(() -> fontPreferences);
  private String schemeName;
  private boolean canBeDeleted = true;
  // version influences an XML format and triggers migration
  private int version = CURR_VERSION;
  private Color deprecatedBackgroundColor = null;
  //endregion

  protected AbstractColorsScheme(EditorColorsScheme parentScheme) {
    this.parentScheme = parentScheme;
  }

  public AbstractColorsScheme() {
  }

  public void setDefaultMetaInfo(@Nullable AbstractColorsScheme parentScheme) {
    metaInfo.setProperty(META_INFO_IDE, PlatformUtils.getPlatformPrefix());
    metaInfo.setProperty(META_INFO_IDE_VERSION, ApplicationInfo.getInstance().getStrictVersion());
    if (parentScheme != null &&
        parentScheme != EmptyColorScheme.getEmptyScheme() &&
        !parentScheme.getName().startsWith(Scheme.EDITABLE_COPY_PREFIX)) {
      metaInfo.setProperty(META_INFO_ORIGINAL, parentScheme.getName());
      String pluginId = parentScheme.getMetaProperties().getProperty(META_INFO_PLUGIN_ID);
      if (pluginId != null) {
        metaInfo.setProperty(META_INFO_PLUGIN_ID, pluginId);
      }
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
    return schemeName;
  }

  @Override
  public void setName(@NotNull String name) {
    schemeName = name;
  }

  @Override
  public @NotNull String getDisplayName() {
    if (!isReadOnly()) {
      EditorColorsScheme original = getOriginal();
      if (original != null && original.isReadOnly()) {
        return original.getDisplayName();
      }
    }

    String localizedName = getLocalizedName();
    if (localizedName == null) {
      //noinspection HardCodedStringLiteral
      return Scheme.getBaseName(getName());
    }
    return localizedName;
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

  public void copyTo(@NotNull AbstractColorsScheme newScheme) {
    boolean noName = newScheme.schemeName == null;
    boolean consoleDelegating = consoleFontPreferences instanceof DelegatingFontPreferences;
    boolean editorDelegating = fontPreferences instanceof DelegatingFontPreferences;

    if (consoleDelegating) {
      newScheme.setUseEditorFontPreferencesInConsole();
    }
    else {
      if (LOG.isDebugEnabled()) {
        LOG.debug("setConsoleFontPreferences: " + consoleFontPreferences.getFontFamily() + ":" + consoleFontPreferences.getSize(consoleFontPreferences.getFontFamily()));
      }
      newScheme.setConsoleFontPreferences(consoleFontPreferences);
    }
    if (editorDelegating) {
      newScheme.setUseAppFontPreferencesInEditor();
    }
    else {
      if (LOG.isDebugEnabled()) {
        LOG.debug("setFontPreferences: " + fontPreferences.getFontFamily() +":"+fontPreferences.getSize(fontPreferences.getFontFamily()));
      }
      newScheme.setFontPreferences(fontPreferences);
    }

    if (LOG.isDebugEnabled()) {
      String delegateInfo = ". Delegate info: Console: " + consoleDelegating + " Editor: " + editorDelegating;
      if (noName) {
        LOG.debug("Copying scheme " + debugSchemeName() + " to empty newScheme" + delegateInfo);
      } else {
        LOG.debug("Copying scheme " + debugSchemeName() + " to " + newScheme.debugSchemeName() + delegateInfo);
      }
    }

    newScheme.attributesMap = new HashMap<>(attributesMap);
    newScheme.colorMap = new HashMap<>(colorMap);
    newScheme.version = version;
  }

  public @NotNull Set<ColorKey> getColorKeys() {
    return colorMap.keySet();
  }

  /**
   * Returns attributes defined in this scheme (not inherited from a parent).
   */
  public @NotNull Map<@NonNls String, TextAttributes> getDirectlyDefinedAttributes() {
    return new HashMap<>(attributesMap);
  }

  /**
   * Returns colors defined in this scheme (not inherited from a parent).
   */
  public @NotNull Map<ColorKey, Color> getDirectlyDefinedColors() {
    return new HashMap<>(colorMap);
  }

  @Override
  public void setUseAppFontPreferencesInEditor() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("setUseAppFontPreferencesInEditor in " + debugSchemeName());
    }
    fontPreferences = new DelegatingFontPreferences(() -> AppEditorFontOptions.getInstance().getFontPreferences());
    initFonts();
  }

  @Override
  public boolean isUseAppFontPreferencesInEditor() {
    return fontPreferences instanceof DelegatingFontPreferences;
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
    return fontPreferences instanceof DelegatingFontPreferences ? EditorFontCache.getInstance().getFont(key) : super.getFont(key);
  }

  @Override
  public @NotNull FontPreferences getFontPreferences() {
    return fontPreferences;
  }

  @Override
  public void setFontPreferences(@NotNull FontPreferences preferences) {
    preferences.copyTo(ensureEditableFontPreferences());
    initFonts();
  }

  @Override
  public @NlsSafe String getEditorFontName() {
    return fontPreferences.getFontFamily();
  }

  @Override
  public void setEditorFontName(String fontName) {
    ModifiableFontPreferences currPreferences = ensureEditableFontPreferences();
    boolean useLigatures = currPreferences.useLigatures();
    float editorFontSize = getEditorFontSize2D();
    if (LOG.isDebugEnabled()) {
      String message = "setEditorFontName=%s for %s. Current name/size: %s:%.2f".formatted(
        fontName, debugSchemeName(), currPreferences.getFontFamily(), editorFontSize);
      Throwable stack = new Throwable(message);
      LOG.debug(message, stack);
    }
    currPreferences.clear();
    currPreferences.register(fontName, editorFontSize);
    currPreferences.setUseLigatures(useLigatures);
    initFonts();
  }

  @Override
  public int getEditorFontSize() {
    return fontPreferences.getSize(fontPreferences.getFontFamily());
  }

  @Override
  public void setEditorFontSize(int fontSize) {
    setEditorFontSize((float)fontSize);
  }

  @Override
  public void setEditorFontSize(float fontSize) {
    float fixedFontSize = EditorFontsConstants.checkAndFixEditorFontSize(fontSize);
    if (LOG.isDebugEnabled()) {
      String message = "setEditorFontSize for %s. %.2f (original: %.2f)".formatted(
        debugSchemeName(), fixedFontSize, fontSize);
      Throwable stack = new Throwable(message);
      LOG.debug(message, stack);
    }
    ensureEditableFontPreferences().register(fontPreferences.getFontFamily(), fixedFontSize);
    initFonts();
    setSaveNeeded(true);
  }

  @Override
  public float getEditorFontSize2D() {
    return fontPreferences.getSize2D(fontPreferences.getFontFamily());
  }

  @Override
  public float getLineSpacing() {
    return fontPreferences.getLineSpacing();
  }

  @Override
  public void setLineSpacing(float lineSpacing) {
    ensureEditableFontPreferences().setLineSpacing(lineSpacing);
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
    version = CURR_VERSION;
  }

  private void readScheme(@NotNull Element node) {
    deprecatedBackgroundColor = null;
    if (!SCHEME_ELEMENT.equals(node.getName())) {
      return;
    }

    setName(node.getAttributeValue(NAME_ATTR));
    int readVersion = Integer.parseInt(node.getAttributeValue(VERSION_ATTR, "0"));
    if (readVersion > CURR_VERSION) {
      throw new IllegalStateException("Unsupported color scheme version: " + readVersion);
    }

    version = readVersion;
    String isDefaultScheme = node.getAttributeValue(DEFAULT_SCHEME_ATTR);
    boolean isDefault = isDefaultScheme != null && Boolean.parseBoolean(isDefaultScheme);
    if (!isDefault) {
      parentScheme = getDefaultScheme(node.getAttributeValue(PARENT_SCHEME_ATTR, EmptyColorScheme.getSchemeName()));
    }

    metaInfo.clear();
    Ref<Float> fontScale = new Ref<>();
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

    if (deprecatedBackgroundColor != null) {
      TextAttributes textAttributes = attributesMap.get(HighlighterColors.TEXT.getExternalName());
      if (textAttributes == null) {
        textAttributes = new TextAttributes(Color.black, deprecatedBackgroundColor, null, EffectType.BOXED, Font.PLAIN);
        attributesMap.put(HighlighterColors.TEXT.getExternalName(), textAttributes);
      }
      else {
        textAttributes.setBackgroundColor(deprecatedBackgroundColor);
      }
    }

    if (consoleFontPreferences.getEffectiveFontFamilies().isEmpty()) {
      fontPreferences.copyTo(consoleFontPreferences);
    }

    initFonts();
  }

  private void readMetaInfo(@NotNull Element metaInfoElement) {
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

  public void readAttributes(@NotNull Element childNode) {
    for (Element e : childNode.getChildren(OPTION_ELEMENT)) {
      String keyName = e.getAttributeValue(NAME_ATTR);
      Element valueElement = e.getChild(VALUE_ELEMENT);
      TextAttributes attr = valueElement != null ? myValueReader.read(TextAttributes.class, valueElement) :
                            e.getAttributeValue(BASE_ATTRIBUTES_ATTR) != null ? INHERITED_ATTRS_MARKER :
                            null;
      if (attr != null) {
        attributesMap.put(keyName, attr);
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
        deprecatedBackgroundColor = valueColor;
      }
      ColorKey name = ColorKey.find(keyName);
      colorMap.put(name, valueColor == null ? NULL_COLOR_MARKER : valueColor);
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
    parentNode.setAttribute(VERSION_ATTR, Integer.toString(version));

    /*
     * FONT_SCALE is used to correctly identify the font size in both the JRE-managed HiDPI mode and
     * the IDE-managed HiDPI mode: {@link UIUtil#isJreHiDPIEnabled()}. Also, it helps to distinguish
     * the "hidpi-aware" scheme version from the previous one. Namely, the absence of the FONT_SCALE
     * attribute in the scheme indicates the previous "hidpi-unaware" scheme and the restored font size
     * is reset to default. It's assumed this (transition case) happens only once, after which the IDE
     * will be able to restore the font size according to its scale and the IDE HiDPI mode. The default
     * FONT_SCALE value should also be written for that reason.
     */
    if (!(fontPreferences instanceof DelegatingFontPreferences) || !(consoleFontPreferences instanceof DelegatingFontPreferences)) {
      // must precede font options
      JdomKt.addOptionTag(parentNode, FONT_SCALE, String.valueOf(UISettings.getDefFontScale()));
    }

    if (parentScheme != null && parentScheme != EmptyColorScheme.getEmptyScheme()) {
      parentNode.setAttribute(PARENT_SCHEME_ATTR, parentScheme.getName());
    }

    if (!"true".equals(metaInfo.getProperty(META_INFO_PARTIAL)) && !isReadOnly() && getBaseScheme() != parentScheme) {
      metaInfo.setProperty(META_INFO_PARTIAL, "true");
    }

    if (!metaInfo.isEmpty()) {
      parentNode.addContent(metaInfoToElement());
    }

    // IJ has used a 'single customizable font' mode for ages. That's why we want to support that format now, when it's possible
    // to specify a font sequence (see getFontPreferences()), there are big chances that many clients still will use a single font.
    // That's why we want to use an old format when zero or one font is selected and 'extended' format otherwise.
    boolean useOldFontFormat = fontPreferences.getEffectiveFontFamilies().size() <= 1;
    if (!(fontPreferences instanceof DelegatingFontPreferences)) {
      JdomKt.addOptionTag(parentNode, LINE_SPACING, String.valueOf(getLineSpacing()));
      if (useOldFontFormat) {
        JdomKt.addOptionTag(parentNode, EDITOR_FONT_SIZE, String.valueOf(getEditorFontSize()));
        JdomKt.addOptionTag(parentNode, EDITOR_FONT_NAME, fontPreferences.getFontFamily());
      }
      else {
        writeFontPreferences(EDITOR_FONT, parentNode, fontPreferences);
      }
      writeLigaturesPreferences(parentNode, fontPreferences, EDITOR_LIGATURES);
    }

    if (!(consoleFontPreferences instanceof DelegatingFontPreferences)) {
      if (consoleFontPreferences.getEffectiveFontFamilies().size() <= 1) {
        JdomKt.addOptionTag(parentNode, CONSOLE_FONT_NAME, getConsoleFontName());

        if (getConsoleFontSize() != getEditorFontSize()) {
          // Write font size as integer for compatibility
          JdomKt.addOptionTag(parentNode, CONSOLE_FONT_SIZE, Integer.toString(getConsoleFontSize()));
        }
      }
      else {
        writeFontPreferences(CONSOLE_FONT, parentNode, consoleFontPreferences);
      }
      writeLigaturesPreferences(parentNode, consoleFontPreferences, CONSOLE_LIGATURES);
      if ((fontPreferences instanceof DelegatingFontPreferences) || getConsoleLineSpacing() != getLineSpacing()) {
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

    setSaveNeeded(false);
  }

  private void writeAttributes(@NotNull Element attrElements) {
    List<Map.Entry<String, TextAttributes>> list = new ArrayList<>(attributesMap.entrySet());
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
    EditorColorsScheme baseScheme = getBaseScheme();
    if (attributes == INHERITED_ATTRS_MARKER) {
      TextAttributesKey baseKey = key.getFallbackAttributeKey();
      // IDEA-162774 do not store if inheritance = on in the parent scheme
      TextAttributes parentAttributes = baseScheme instanceof AbstractColorsScheme ?
                                        ((AbstractColorsScheme)baseScheme).getDirectlyDefinedAttributes(key) : null;
      boolean parentOverwritingInheritance = parentAttributes != null && parentAttributes != INHERITED_ATTRS_MARKER;
      if (parentOverwritingInheritance) {
        attrElements.addContent(new Element(OPTION_ELEMENT)
                                  .setAttribute(NAME_ATTR, key.getExternalName())
                                  .setAttribute(BASE_ATTRIBUTES_ATTR, baseKey != null ? baseKey.getExternalName() : ""));
      }
      return;
    }

    if (baseScheme != null) {
      // fallback attributes must be not used, otherwise derived scheme as copy will not have such key
      TextAttributes parentAttributes = baseScheme instanceof AbstractColorsScheme
                                        ? ((AbstractColorsScheme)baseScheme).getDirectlyDefinedAttributes(key)
                                        : baseScheme.getAttributes(key);
      if (attributes.equals(parentAttributes)) {
        return;
      }
    }

    Element valueElement = new Element(VALUE_ELEMENT);
    attributes.writeExternal(valueElement);
    attrElements.addContent(new Element(OPTION_ELEMENT).setAttribute(NAME_ATTR, key.getExternalName()).addContent(valueElement));
  }

  public void optimizeAttributeMap() {
    EditorColorsScheme parentScheme = this.parentScheme;
    if (parentScheme == null) {
      return;
    }

    attributesMap.entrySet().removeIf(entry -> {
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

    colorMap.keySet().removeAll(colorMap.keySet().stream().filter(key -> {
      Color color = colorMap.get(key);
      if (color == INHERITED_COLOR_MARKER) {
        return !hasExplicitlyDefinedColors(parentScheme, key);
      }

      Color parent = parentScheme instanceof DefaultColorsScheme
                     ? ((DefaultColorsScheme)parentScheme).getColor(key, false)
                     : parentScheme.getColor(key);
      return Comparing.equal(parent, color == NULL_COLOR_MARKER ? null : color);
    }).collect(Collectors.toSet()));
  }

  private @NotNull Element metaInfoToElement() {
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

  private void writeColors(@NotNull Element colorElements) {
    List<ColorKey> list = new ArrayList<>(colorMap.keySet());
    list.sort(null);
    for (ColorKey key : list) {
      writeColor(colorElements, key);
    }
  }

  private void writeColor(@NotNull Element colorElements, @NotNull ColorKey key) {
    EditorColorsScheme baseScheme = getBaseScheme();
    Color color = colorMap.get(key);
    if (color == INHERITED_COLOR_MARKER) {
      ColorKey fallbackKey = key.getFallbackColorKey();
      Color parentFallback = baseScheme instanceof AbstractColorsScheme ?
                             ((AbstractColorsScheme)baseScheme).getDirectlyDefinedColor(key) : null;
      boolean parentOverwritingInheritance = parentFallback != null && parentFallback != INHERITED_COLOR_MARKER;
      if (fallbackKey != null && parentOverwritingInheritance) {
        colorElements.addContent(new Element(OPTION_ELEMENT)
                                   .setAttribute(NAME_ATTR, key.getExternalName())
                                   .setAttribute(BASE_ATTRIBUTES_ATTR, fallbackKey.getExternalName()));
      }
      return;
    }

    if (baseScheme != null) {
      Color parent = baseScheme instanceof AbstractColorsScheme
                     ? ((AbstractColorsScheme)baseScheme).getDirectlyDefinedColor(key)
                     : baseScheme.getColor(key);
      if (parent != null && colorsEqual(color, parent)) {
        return;
      }
    }

    String rgb = "";
    if (color != NULL_COLOR_MARKER) {
      rgb = Integer.toString(0xFFFFFF & color.getRGB(), 16);
      int alpha = 0xFF & color.getAlpha();
      if (alpha != 0xFF) {
        rgb += Integer.toString(alpha, 16);
      }
    }
    JdomKt.addOptionTag(colorElements, key.getExternalName(), rgb);
  }

  final @Nullable EditorColorsScheme getBaseScheme() {
    if (schemeName.startsWith(Scheme.EDITABLE_COPY_PREFIX)) {
      EditorColorsScheme original = getOriginal();
      if (original != null) {
        return original;
      }
    }
    return parentScheme;
  }

  private ModifiableFontPreferences ensureEditableFontPreferences() {
    if (!(fontPreferences instanceof ModifiableFontPreferences)) {
      ModifiableFontPreferences editableFontPreferences = new FontPreferencesImpl();
      fontPreferences.copyTo(editableFontPreferences);
      if (LOG.isDebugEnabled()) {
        String message = "ensureEditableFontPreferences in %s. %s, size: %d".formatted(
          debugSchemeName(), editableFontPreferences, editableFontPreferences.getSize(editableFontPreferences.getFontFamily()));
        Throwable stack = new Throwable(message);
        LOG.debug(message, stack);
      }
      fontPreferences = editableFontPreferences;
      ((FontPreferencesImpl)fontPreferences).addChangeListener((source) -> initFonts());
    }
    return (ModifiableFontPreferences)fontPreferences;
  }

  @Override
  public @NotNull FontPreferences getConsoleFontPreferences() {
    AppConsoleFontOptions consoleFontOptions = AppConsoleFontOptions.getInstance();
    return consoleFontOptions.isUseEditorFont() ? consoleFontPreferences : consoleFontOptions.getFontPreferences();
  }

  @Override
  public void setConsoleFontPreferences(@NotNull FontPreferences preferences) {
    preferences.copyTo(ensureEditableConsoleFontPreferences());
    initFonts();
  }

  @Override
  public void setUseEditorFontPreferencesInConsole() {
    consoleFontPreferences = new DelegatingFontPreferences(() -> fontPreferences);
    initFonts();
  }

  @Override
  public boolean isUseEditorFontPreferencesInConsole() {
    return consoleFontPreferences instanceof DelegatingFontPreferences;
  }

  @Override
  public @NotNull @NlsSafe String getConsoleFontName() {
    return getConsoleFontPreferences().getFontFamily();
  }

  @Override
  public void setConsoleFontName(String fontName) {
    ModifiableFontPreferences consolePreferences = ensureEditableConsoleFontPreferences();
    float consoleFontSize = getConsoleFontSize2D();
    consolePreferences.clear();
    consolePreferences.register(fontName, consoleFontSize);
  }

  private ModifiableFontPreferences ensureEditableConsoleFontPreferences() {
    if (!(consoleFontPreferences instanceof ModifiableFontPreferences)) {
      ModifiableFontPreferences editableFontPreferences = new FontPreferencesImpl();
      consoleFontPreferences.copyTo(editableFontPreferences);
      consoleFontPreferences = editableFontPreferences;
    }
    return (ModifiableFontPreferences)consoleFontPreferences;
  }

  @Override
  public int getConsoleFontSize() {
    return (int)(getConsoleFontSize2D() + 0.5);
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
  public float getConsoleFontSize2D() {
    String font = getConsoleFontName();
    UISettings uiSettings = UISettings.getInstanceOrNull();
    if ((uiSettings == null || !uiSettings.getPresentationMode()) && getConsoleFontPreferences().hasSize(font)) {
      return getConsoleFontPreferences().getSize2D(font);
    }
    return getEditorFontSize2D();
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
      if (next == null) {
        return null;
      }
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
    TextAttributes attributes = attributesMap.get(key.getExternalName());
    if (attributes != null) {
      return attributes;
    }
    return parentScheme instanceof AbstractColorsScheme ? ((AbstractColorsScheme)parentScheme).getDirectlyDefinedAttributes(key) : null;
  }

  /**
   * Looks for explicitly specified color either in the scheme or its parent scheme. No fallback keys are used.
   *
   * @param key The key to use for search.
   * @return Explicitly defined color or {@code null} if not found.
   */
  public @Nullable Color getDirectlyDefinedColor(@NotNull ColorKey key) {
    Color color = colorMap.get(key);
    if (color != null) {
      return color;
    }
    return parentScheme instanceof AbstractColorsScheme ? ((AbstractColorsScheme)parentScheme).getDirectlyDefinedColor(key) : null;
  }

  @Override
  public abstract @NotNull SchemeState getSchemeState();

  public void setSaveNeeded(boolean value) {
  }

  @Override
  public @NotNull Properties getMetaProperties() {
    return metaInfo;
  }

  public boolean canBeDeleted() {
    return canBeDeleted;
  }

  public void setCanBeDeleted(boolean canBeDeleted) {
    this.canBeDeleted = canBeDeleted;
  }

  public abstract boolean isVisible();

  @SuppressWarnings("StaticNonFinalField") @VisibleForTesting
  public static @NotNull Function<String, EditorColorsScheme> getSchemeById = it -> EditorColorsManager.getInstance().getScheme(it);

  public @Nullable AbstractColorsScheme getOriginal() {
    String originalSchemeName = getMetaProperties().getProperty(META_INFO_ORIGINAL);
    if (originalSchemeName != null) {
      EditorColorsScheme originalScheme = getSchemeById.apply(originalSchemeName);
      if (originalScheme != this && originalScheme instanceof AbstractColorsScheme) {
        return (AbstractColorsScheme)originalScheme;
      }
    }
    return null;
  }

  public EditorColorsScheme getParentScheme() {
    return parentScheme;
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
    return settingsEqual(other, colorKeyFilter, false);
  }

  public boolean settingsEqual(Object other, @Nullable Predicate<? super ColorKey> colorKeyFilter, boolean ignoreMetaInfo) {
    return settingsEqual(other, colorKeyFilter, false, true);
  }

  public boolean settingsEqual(Object other, @Nullable Predicate<? super ColorKey> colorKeyFilter, boolean ignoreMetaInfo, boolean useDefaults) {
    if (!(other instanceof AbstractColorsScheme otherScheme)) {
      return false;
    }

    // parent is used only for default schemes (e.g., Darcula bundled in all IDEs (opposite to IDE-specific, like Cobalt))
    if (getBaseDefaultScheme(this) != getBaseDefaultScheme(otherScheme)) {
      return false;
    }

    for (String propertyName : metaInfo.stringPropertyNames()) {
      if (ignoreMetaInfo ||
          propertyName.equals(META_INFO_CREATION_TIME) ||
          propertyName.equals(META_INFO_MODIFIED_TIME) ||
          propertyName.equals(META_INFO_IDE) ||
          propertyName.equals(META_INFO_IDE_VERSION) ||
          propertyName.equals(META_INFO_ORIGINAL)
      ) {
        continue;
      }

      if (!Objects.equals(metaInfo.getProperty(propertyName), otherScheme.metaInfo.getProperty(propertyName))) {
        return false;
      }
    }

    return areDelegatingOrEqual(fontPreferences, otherScheme.getFontPreferences()) &&
           areDelegatingOrEqual(consoleFontPreferences, otherScheme.getConsoleFontPreferences()) &&
           attributesEqual(otherScheme, useDefaults) &&
           colorsEqual(otherScheme, colorKeyFilter);
  }

  protected boolean attributesEqual(AbstractColorsScheme otherScheme, boolean useDefaults) {
    return attributesMap.equals(otherScheme.attributesMap);
  }

  protected boolean colorsEqual(AbstractColorsScheme otherScheme, @Nullable Predicate<? super ColorKey> colorKeyFilter) {
    if (colorMap.size() != otherScheme.colorMap.size()) return false;
    for (Map.Entry<ColorKey, Color> entry : colorMap.entrySet()) {
      Color c1 = entry.getValue();
      ColorKey key = entry.getKey();
      Color c2 = otherScheme.colorMap.get(key);
      if (!colorsEqual(c1, c2)) return false;
    }
    return true;
  }

  public void setParent(@NotNull EditorColorsScheme newParent) {
    assert newParent.isReadOnly() : "New parent scheme must be read-only";
    parentScheme = newParent;
  }

  void resolveParent(@NotNull Function<? super String, ? extends EditorColorsScheme> nameResolver) {
    if (parentScheme instanceof TemporaryParent) {
      String parentName = ((TemporaryParent)parentScheme).getParentName();
      EditorColorsScheme newParent = nameResolver.apply(parentName);
      if (newParent == null || !newParent.isReadOnly()) {
        throw new InvalidDataException(parentName);
      }
      parentScheme = newParent;
    }
    else {
      String originalSchemeName = getMetaProperties().getProperty(META_INFO_ORIGINAL);
      String isPartial = getMetaProperties().getProperty(META_INFO_PARTIAL);
      if (originalSchemeName != null && isPartial != null && Boolean.parseBoolean(isPartial)) {
        EditorColorsScheme original = nameResolver.apply(originalSchemeName);
        if (original == null) {
          throw new InvalidDataException(originalSchemeName);
        }
      }
    }
  }

  void copyMissingAttributes(@NotNull AbstractColorsScheme sourceScheme) {
    sourceScheme.colorMap.forEach((key, color) -> colorMap.putIfAbsent(key, color));
    sourceScheme.attributesMap.forEach((key, attributes) -> attributesMap.putIfAbsent(key, attributes));
  }

  private String debugSchemeName(){
    try {
      if (schemeName != null) {
        return schemeName;
      }
      if (parentScheme == null) {
        return null;
      }
      return parentScheme.getName();
    } catch (Throwable e) {
      LOG.warn("An exception occurred while trying to get scheme name", e);
      return "null(e)";
    }
  }

  private static @NotNull EditorColorsScheme getDefaultScheme(@NotNull String name) {
    DefaultColorSchemesManager manager = DefaultColorSchemesManager.getInstance();
    EditorColorsScheme defaultScheme = manager.getScheme(name);
    if (defaultScheme == null) {
      defaultScheme = new TemporaryParent(name);
    }
    return defaultScheme;
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

  protected static boolean colorsEqual(@Nullable Color c1, @Nullable Color c2) {
    if (c1 == NULL_COLOR_MARKER) return c1 == c2;
    return Comparing.equal(c1, c2 == NULL_COLOR_MARKER ? null : c2);
  }

  public static boolean isVisible(@NotNull EditorColorsScheme scheme) {
    return !(scheme instanceof AbstractColorsScheme) || ((AbstractColorsScheme)scheme).isVisible();
  }

  protected static boolean areDelegatingOrEqual(@NotNull FontPreferences preferences1, @NotNull FontPreferences preferences2) {
    boolean isDelegating1 = preferences1 instanceof DelegatingFontPreferences;
    boolean isDelegating2 = preferences2 instanceof DelegatingFontPreferences;
    return isDelegating1 || isDelegating2 ? isDelegating1 && isDelegating2 : preferences1.equals(preferences2);
  }

  private static @Nullable EditorColorsScheme getBaseDefaultScheme(@NotNull EditorColorsScheme scheme) {
    if (!(scheme instanceof AbstractColorsScheme)) {
      return null;
    }
    if (scheme instanceof DefaultColorsScheme) {
      return scheme;
    }
    EditorColorsScheme parent = ((AbstractColorsScheme)scheme).parentScheme;
    return parent != null ? getBaseDefaultScheme(parent) : null;
  }

  private static class TemporaryParent extends EditorColorsSchemeImpl {

    private final String myParentName;

    TemporaryParent(@NotNull String parentName) {
      super(EmptyColorScheme.getEmptyScheme());
      myParentName = parentName;
    }

    public @NonNls String getParentName() {
      return myParentName;
    }
  }
}
