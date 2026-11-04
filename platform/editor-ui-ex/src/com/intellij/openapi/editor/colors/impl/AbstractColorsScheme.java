// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.BundleBase;
import com.intellij.DynamicBundle;
import com.intellij.application.options.EditorFontsConstants;
import com.intellij.configurationStore.SerializableScheme;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.DelegatingFontPreferences;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontCache;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.ModifiableFontPreferences;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemeState;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.JBColor;
import com.intellij.util.PlatformUtils;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.awt.Color;
import java.awt.Font;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

@SuppressWarnings("UseJBColor")
public abstract class AbstractColorsScheme extends EditorFontCache implements EditorColorsScheme, SerializableScheme {
  private static final Logger LOG = Logger.getInstance(AbstractColorsScheme.class);

  public static boolean isVisible(@NotNull EditorColorsScheme scheme) {
    return !(scheme instanceof AbstractColorsScheme absScheme) || absScheme.isVisible();
  }

  public static final int CURR_VERSION = 142;
  public static final TextAttributes INHERITED_ATTRS_MARKER = new TextAttributes();
  public static final Color INHERITED_COLOR_MARKER = JBColor.marker("INHERITED_COLOR_MARKER");
  public static final Color NULL_COLOR_MARKER = JBColor.marker("NULL_COLOR_MARKER");
  public static final String NAME_BUNDLE_PROPERTY = "lcNameBundle";
  public static final String NAME_KEY_PROPERTY = "lcNameKey";
  public static final @NonNls String NAME_ATTR = "name";
  public static final @NonNls String META_INFO_PLUGIN_ID = "pluginId";

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
  private static final @NonNls String META_INFO_ORIGINAL_SCHEME = "originalScheme";
  /**
   * @deprecated Use {@link #getOriginal()} to get the original scheme.
   */
  @Deprecated
  public static final @NonNls String META_INFO_ORIGINAL = META_INFO_ORIGINAL_SCHEME;
  static final @NonNls String META_INFO_PARTIAL = "partialSave";

  @ApiStatus.Internal
  public static final String ENABLE_RUNTIME_SCHEME_COLOR_WRAPPER_OPTION = "editor.color.scheme.mark.colors";

  @SuppressWarnings("StaticNonFinalField")
  @VisibleForTesting
  public static @NotNull Function<String, EditorColorsScheme> getSchemeById = it -> EditorColorsManager.getInstance().getScheme(it);

  private static final AtomicReferenceFieldUpdater<AbstractColorsScheme, ColorsSchemeSnapshot> SNAPSHOT_UPDATER =
    AtomicReferenceFieldUpdater.newUpdater(AbstractColorsScheme.class, ColorsSchemeSnapshot.class, "snapshot");

  private final Properties metaInfo = new Properties();
  private final EditorFontCache fontCache = new EditorFontCacheImpl(this::getFontCacheScheme);
  private volatile @NotNull ColorsSchemeSnapshot snapshot;
  private final FontPreferences appFontDelegate =
    new DelegatingFontPreferences(() -> AppEditorFontOptions.getInstance().getFontPreferences());
  private final FontPreferences editorFontDelegate = new DelegatingFontPreferences(() -> fontPreferencesOf(snapshot));
  private final FontPreferencesImpl editorFontView = new FontPreferencesImpl(new EditorFontStorage());
  private final FontPreferencesImpl consoleFontView = new FontPreferencesImpl(new ConsoleFontStorage());

  protected AbstractColorsScheme(@Nullable EditorColorsScheme parentScheme) {
    snapshot = ColorsSchemeSnapshot.initial().withParentScheme(parentScheme);
  }

  public AbstractColorsScheme() {
    this(null);
  }

  @Override
  public @NotNull Color getDefaultBackground() {
    Color c = getAttributes(HighlighterColors.TEXT).getBackgroundColor();
    return c != null ? c : Color.white;
  }

  @Override
  public @NotNull Color getDefaultForeground() {
    Color c = getAttributes(HighlighterColors.TEXT).getForegroundColor();
    return c != null ? c : Color.black;
  }

  @Override
  public @NotNull String getName() {
    String schemeName = snapshot.schemeName();
    if (schemeName == null) {
      throw new IllegalStateException("The scheme has no name, setName was not called");
    }
    return schemeName;
  }

  @Override
  public void setName(@NotNull String name) {
    updateSnapshot(current -> current.withSchemeName(name));
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

  @Override
  public abstract Object clone();

  @Override
  public void setUseAppFontPreferencesInEditor() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("setUseAppFontPreferencesInEditor in " + debugSchemeName());
    }
    updateSnapshot(current -> current.withUseAppFontPreferencesInEditor(true));
  }

  @Override
  public boolean isUseAppFontPreferencesInEditor() {
    return snapshot.useAppFontPreferencesInEditor();
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
  public @NotNull Font getFont(@Nullable EditorFontType key) {
    if (snapshot.useAppFontPreferencesInEditor()) {
      return getInstance().getFont(key);
    }
    return fontCache.getFont(key);
  }

  @Override
  public void reset() {
    fontCache.reset();
  }

  @Override
  public @NotNull FontPreferences getFontPreferences() {
    if (snapshot.useAppFontPreferencesInEditor()) {
      return appFontDelegate;
    }
    return editorFontView;
  }

  @Override
  public void setFontPreferences(@NotNull FontPreferences preferences) {
    preferences.copyTo(ensureEditableFontPreferences());
  }

  @Override
  public @NlsSafe String getEditorFontName() {
    return fontPreferencesOf(snapshot).getFontFamily();
  }

  @Override
  public void setEditorFontName(String fontName) {
    ModifiableFontPreferences currPreferences = ensureEditableFontPreferences();
    float editorFontSize = getEditorFontSize2D();
    if (LOG.isDebugEnabled()) {
      String message = "setEditorFontName=%s for %s. Current name/size: %s:%.2f".formatted(
        fontName,
        debugSchemeName(),
        currPreferences.getFontFamily(),
        editorFontSize
      );
      Throwable stack = new Throwable(message);
      LOG.debug(message, stack);
    }
    currPreferences.runBatchUpdate(draft -> {
      boolean useLigatures = draft.useLigatures();
      draft.clear();
      draft.register(fontName, editorFontSize);
      draft.setUseLigatures(useLigatures);
    });
  }

  @Override
  public int getEditorFontSize() {
    FontPreferences preferences = fontPreferencesOf(snapshot);
    return preferences.getSize(preferences.getFontFamily());
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
        debugSchemeName(), fixedFontSize, fontSize
      );
      Throwable stack = new Throwable(message);
      LOG.debug(message, stack);
    }
    ModifiableFontPreferences preferences = ensureEditableFontPreferences();
    preferences.register(preferences.getFontFamily(), fixedFontSize);
    setSaveNeeded(true);
  }

  @Override
  public float getEditorFontSize2D() {
    FontPreferences preferences = fontPreferencesOf(snapshot);
    return preferences.getSize2D(preferences.getFontFamily());
  }

  @Override
  public float getLineSpacing() {
    return fontPreferencesOf(snapshot).getLineSpacing();
  }

  @Override
  public void setLineSpacing(float lineSpacing) {
    ensureEditableFontPreferences().setLineSpacing(lineSpacing);
  }

  @Override
  public @NonNls String toString() {
    try {
      return getName();
    } catch (IllegalStateException e) {
      // getName() throws for a scheme without a name, and a subclass can keep the name in its own field
      return "<unnamed scheme>";
    }
  }

  @Override
  public void readExternal(@NotNull Element parentNode) {
    ColorsSchemeSerDe.readExternal(this, parentNode);
  }

  @Override
  public @NotNull FontPreferences getConsoleFontPreferences() {
    AppConsoleFontOptions consoleFontOptions = AppConsoleFontOptions.getInstance();
    if (!consoleFontOptions.isUseEditorFont()) {
      return consoleFontOptions.getFontPreferences();
    }
    if (snapshot.useEditorFontPreferencesInConsole()) {
      return editorFontDelegate;
    }
    return consoleFontView;
  }

  @Override
  public void setConsoleFontPreferences(@NotNull FontPreferences preferences) {
    preferences.copyTo(ensureEditableConsoleFontPreferences());
  }

  @Override
  public void setUseEditorFontPreferencesInConsole() {
    updateSnapshot(current -> current.withUseEditorFontPreferencesInConsole(true));
  }

  @Override
  public boolean isUseEditorFontPreferencesInConsole() {
    return snapshot.useEditorFontPreferencesInConsole();
  }

  @Override
  public @NotNull @NlsSafe String getConsoleFontName() {
    return getConsoleFontPreferences().getFontFamily();
  }

  @Override
  public void setConsoleFontName(String fontName) {
    ModifiableFontPreferences consolePreferences = ensureEditableConsoleFontPreferences();
    float consoleFontSize = consoleFontSize2D(snapshot);
    consolePreferences.runBatchUpdate(draft -> {
      draft.clear();
      draft.register(fontName, consoleFontSize);
    });
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
    ModifiableFontPreferences consolePreferences = ensureEditableConsoleFontPreferences();
    float fixedFontSize = EditorFontsConstants.checkAndFixEditorFontSize(fontSize);
    consolePreferences.register(consolePreferences.getFontFamily(), fixedFontSize);
  }

  @Override
  public float getConsoleFontSize2D() {
    String font = getConsoleFontName();
    UISettings uiSettings = UISettings.getInstanceOrNull();
    if ((uiSettings == null || !uiSettings.getPresentationMode()) &&
        getConsoleFontPreferences().hasSize(font)) {
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

  @Override
  public abstract @NotNull SchemeState getSchemeState();

  @Override
  public @NotNull Properties getMetaProperties() {
    return metaInfo;
  }

  @Override
  public @NotNull Element writeScheme() {
    Element root = new Element("scheme");
    writeExternal(root);
    return root;
  }

  public void setDefaultMetaInfo(@Nullable AbstractColorsScheme parentScheme) {
    metaInfo.setProperty(META_INFO_IDE, PlatformUtils.getPlatformPrefix());
    metaInfo.setProperty(META_INFO_IDE_VERSION, ApplicationInfo.getInstance().getStrictVersion());
    if (parentScheme != null &&
        parentScheme != EmptyColorScheme.getEmptyScheme() &&
        !parentScheme.getName().startsWith(EDITABLE_COPY_PREFIX)) {
      metaInfo.setProperty(META_INFO_ORIGINAL_SCHEME, parentScheme.getName());
      String pluginId = parentScheme.getMetaProperties().getProperty(META_INFO_PLUGIN_ID);
      if (pluginId != null) {
        metaInfo.setProperty(META_INFO_PLUGIN_ID, pluginId);
      }
    }
  }

  public void copyTo(@NotNull AbstractColorsScheme newScheme) {
    ColorsSchemeSnapshot source = snapshot;
    boolean noName = newScheme.snapshot.schemeName() == null;
    boolean consoleDelegating = source.useEditorFontPreferencesInConsole();
    boolean editorDelegating = source.useAppFontPreferencesInEditor();
    // The setters get the objects that the getters return, so a subclass of the new scheme can compare them by identity
    if (consoleDelegating) {
      newScheme.setUseEditorFontPreferencesInConsole();
    } else {
      if (LOG.isDebugEnabled()) {
        FontPreferences consoleFontPreferences = source.consoleFontPreferences();
        String fontFamily = consoleFontPreferences.getFontFamily();
        LOG.debug("setConsoleFontPreferences: " + fontFamily + ":" + consoleFontPreferences.getSize(fontFamily));
      }
      newScheme.setConsoleFontPreferences(consoleFontView);
    }
    if (editorDelegating) {
      newScheme.setUseAppFontPreferencesInEditor();
    } else {
      if (LOG.isDebugEnabled()) {
        FontPreferences fontPreferences = source.fontPreferences();
        String fontFamily = fontPreferences.getFontFamily();
        LOG.debug("setFontPreferences: " + fontFamily + ":" + fontPreferences.getSize(fontFamily));
      }
      newScheme.setFontPreferences(editorFontView);
    }
    if (LOG.isDebugEnabled()) {
      String delegateInfo = ". Delegate info: Console: " + consoleDelegating + " Editor: " + editorDelegating;
      if (noName) {
        LOG.debug("Copying scheme " + debugSchemeName() + " to empty newScheme" + delegateInfo);
      } else {
        LOG.debug("Copying scheme " + debugSchemeName() + " to " + newScheme.debugSchemeName() + delegateInfo);
      }
    }
    newScheme.updateSnapshot(current -> current.withColorsAndAttributesOf(source).withVersion(source.version()));
  }

  /**
   * Returns the keys of the colors that this scheme defines itself.
   * The set is unmodifiable, and a later change of the scheme does not change it.
   */
  public @NotNull Set<ColorKey> getColorKeys() {
    return snapshot.colors().keySet();
  }

  /**
   * Returns attributes defined in this scheme (not inherited from a parent).
   */
  public @NotNull Map<@NonNls String, TextAttributes> getDirectlyDefinedAttributes() {
    return new HashMap<>(snapshot.attributes());
  }

  /**
   * Returns colors defined in this scheme (not inherited from a parent).
   */
  public @NotNull Map<ColorKey, Color> getDirectlyDefinedColors() {
    return new HashMap<>(snapshot.colors());
  }

  public void readAttributes(@NotNull Element childNode) {
    ColorsSchemeSerDe.readAttributes(this, childNode);
  }

  public void readColors(@NotNull Element childNode) {
    ColorsSchemeSerDe.readColors(this, childNode);
  }

  public void writeExternal(Element parentNode) {
    ColorsSchemeSerDe.writeExternal(this, parentNode);
  }

  public void optimizeAttributeMap() {
    updateSnapshot(current -> {
      EditorColorsScheme parentScheme = current.parentScheme();
      if (parentScheme == null) {
        return current;
      }
      return current
        .withoutAttributes((keyName, attributes) -> parentHasSameAttributes(parentScheme, keyName, attributes))
        .withoutColors((key, color) -> parentHasSameColor(parentScheme, key, color));
    });
  }

  /**
   * Looks for explicitly specified attributes either in the scheme or its parent scheme. No fallback keys are used.
   *
   * @param key The key to use for search.
   * @return Explicitly defined attribute or {@code null} if not found.
   */
  public @Nullable TextAttributes getDirectlyDefinedAttributes(@NotNull TextAttributesKey key) {
    ColorsSchemeSnapshot current = snapshot;
    TextAttributes attributes = current.getAttributes(key.getExternalName());
    if (attributes != null) {
      return attributes;
    }
    if (current.parentScheme() instanceof AbstractColorsScheme absScheme) {
      return absScheme.getDirectlyDefinedAttributes(key);
    }
    return null;
  }

  /**
   * Looks for explicitly specified color either in the scheme or its parent scheme. No fallback keys are used.
   *
   * @param key The key to use for search.
   * @return Explicitly defined color or {@code null} if not found.
   */
  public @Nullable Color getDirectlyDefinedColor(@NotNull ColorKey key) {
    ColorsSchemeSnapshot current = snapshot;
    Color color = current.getColor(key);
    if (color != null) {
      return color;
    }
    if (current.parentScheme() instanceof AbstractColorsScheme absScheme) {
      return absScheme.getDirectlyDefinedColor(key);
    }
    return null;
  }

  public void setSaveNeeded(boolean value) {
  }

  public boolean canBeDeleted() {
    return snapshot.canBeDeleted();
  }

  public void setCanBeDeleted(boolean canBeDeleted) {
    updateSnapshot(current -> current.withCanBeDeleted(canBeDeleted));
  }

  public abstract boolean isVisible();

  public @Nullable AbstractColorsScheme getOriginal() {
    String originalSchemeName = getMetaProperties().getProperty(META_INFO_ORIGINAL_SCHEME);
    if (originalSchemeName != null) {
      EditorColorsScheme originalScheme = getSchemeById.apply(originalSchemeName);
      if (originalScheme != this && originalScheme instanceof AbstractColorsScheme abs) {
        return abs;
      }
    }
    return null;
  }

  public EditorColorsScheme getParentScheme() {
    return snapshot.parentScheme();
  }

  public boolean settingsEqual(Object other) {
    return settingsEqual(other, null);
  }

  public boolean settingsEqual(Object other, @Nullable Predicate<? super ColorKey> colorKeyFilter) {
    return settingsEqual(other, colorKeyFilter, false);
  }

  public boolean settingsEqual(
    Object other,
    @Nullable Predicate<? super ColorKey> colorKeyFilter,
    boolean ignoreMetaInfo
  ) {
    return settingsEqual(other, colorKeyFilter, ignoreMetaInfo, true);
  }

  public boolean settingsEqual(
    Object other,
    @Nullable Predicate<? super ColorKey> colorKeyFilter,
    boolean ignoreMetaInfo,
    boolean useDefaults
  ) {
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
          propertyName.equals(META_INFO_ORIGINAL_SCHEME)) {
        continue;
      }
      if (!Objects.equals(metaInfo.getProperty(propertyName), otherScheme.metaInfo.getProperty(propertyName))) {
        return false;
      }
    }
    ColorsSchemeSnapshot current = snapshot;
    ColorsSchemeSnapshot otherSnapshot = otherScheme.snapshot;
    return areDelegatingOrEqual(fontPreferencesOf(current), otherScheme.fontPreferencesOf(otherSnapshot)) &&
           areDelegatingOrEqual(consoleFontPreferencesOf(current), otherScheme.consoleFontPreferencesOf(otherSnapshot)) &&
           attributesEqual(otherScheme, useDefaults) &&
           colorsEqual(otherScheme, colorKeyFilter);
  }

  public void setParent(@NotNull EditorColorsScheme newParent) {
    assert newParent.isReadOnly() : "New parent scheme must be read-only";
    updateSnapshot(current -> current.withParentScheme(newParent));
  }

  @ApiStatus.Internal
  public void resolveParent(@NotNull Function<? super String, ? extends EditorColorsScheme> nameResolver) {
    if (snapshot.parentScheme() instanceof TemporaryParent temporaryParent) {
      String parentName = temporaryParent.getName();
      EditorColorsScheme newParent = nameResolver.apply(parentName);
      if (newParent == null || !newParent.isReadOnly()) {
        throw new InvalidDataException(parentName);
      }
      updateSnapshot(current -> current.withParentScheme(newParent));
    } else {
      String originalSchemeName = getMetaProperties().getProperty(META_INFO_ORIGINAL_SCHEME);
      String isPartial = getMetaProperties().getProperty(META_INFO_PARTIAL);
      if (originalSchemeName != null && Boolean.parseBoolean(isPartial)) {
        EditorColorsScheme original = nameResolver.apply(originalSchemeName);
        if (original == null) {
          throw new InvalidDataException(originalSchemeName);
        }
      }
    }
  }

  @ApiStatus.Internal
  public void copyMissingAttributes(@NotNull AbstractColorsScheme sourceScheme) {
    ColorsSchemeSnapshot source = sourceScheme.snapshot;
    updateSnapshot(current -> current.withMissing(source));
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

  /**
   * Makes the next font request build the fonts again.
   * The fonts follow the font preferences of this scheme without this call, so call it only after a change outside them.
   */
  protected void initFonts() {
    reset();
  }

  /**
   * Returns the scheme whose font preferences the font cache of this scheme reads.
   *
   * @deprecated The method stays for compatibility.
   * To change the fonts of a scheme, override {@link #getFontPreferences()} and {@link #getConsoleFontPreferences()}.
   */
  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  protected EditorColorsScheme getFontCacheScheme() {
    return this;
  }

  protected @Nullable TextAttributes getFallbackAttributes(@NotNull TextAttributesKey fallbackKey) {
    TextAttributesKey cur = fallbackKey;
    while (true) {
      TextAttributes attrs = getDirectlyDefinedAttributes(cur);
      TextAttributesKey next = cur.getFallbackAttributeKey();
      if (attrs != null && (attrs != INHERITED_ATTRS_MARKER || next == null)) {
        return attrs;
      }
      if (next == null) {
        return null;
      }
      cur = next;
    }
  }

  protected @Nullable Color getFallbackColor(@NotNull ColorKey fallbackKey) {
    ColorKey cur = fallbackKey;
    while (true) {
      Color color = getDirectlyDefinedColor(cur);
      if (color == NULL_COLOR_MARKER) {
        return null;
      }
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

  protected boolean attributesEqual(@NotNull AbstractColorsScheme otherScheme, boolean useDefaults) {
    Map<String, TextAttributes> attributes = snapshot.attributes();
    Map<String, TextAttributes> otherAttributes = otherScheme.snapshot.attributes();
    return attributes.equals(otherAttributes);
  }

  protected boolean colorsEqual(
    @NotNull AbstractColorsScheme otherScheme,
    @Nullable Predicate<? super ColorKey> colorKeyFilter
  ) {
    Map<ColorKey, Color> colors = snapshot.colors();
    Map<ColorKey, Color> otherColors = otherScheme.snapshot.colors();
    if (colors.size() != otherColors.size()) {
      return false;
    }
    for (Map.Entry<ColorKey, Color> entry : colors.entrySet()) {
      Color c1 = entry.getValue();
      ColorKey key = entry.getKey();
      Color c2 = otherColors.get(key);
      if (!colorsEqual(c1, c2)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns the current snapshot of this scheme.
   */
  @NotNull ColorsSchemeSnapshot getSnapshot() {
    return snapshot;
  }

  /**
   * Replaces the snapshot with the result of {@code operation}.
   * The operation can run more than once, so it must have no side effects.
   */
  void updateSnapshot(@NotNull UnaryOperator<ColorsSchemeSnapshot> operation) {
    while (true) {
      ColorsSchemeSnapshot prev = snapshot;
      ColorsSchemeSnapshot next = operation.apply(prev);
      if (next == prev) {
        return;
      }
      if (SNAPSHOT_UPDATER.compareAndSet(this, prev, next)) {
        return;
      }
    }
  }

  /**
   * Returns the editor font preferences of this scheme as a modifiable object.
   * A scheme that uses the application font gets a copy of it as its own editor font first.
   */
  @NotNull ModifiableFontPreferences ensureEditableFontPreferences() {
    while (true) {
      ColorsSchemeSnapshot prev = snapshot;
      if (!prev.useAppFontPreferencesInEditor()) {
        return editorFontView;
      }
      FontPreferencesSnapshot editableFontPreferences = createEditableCopy(appFontDelegate);
      ColorsSchemeSnapshot next = prev
        .withFontPreferences(editableFontPreferences)
        .withUseAppFontPreferencesInEditor(false);
      if (SNAPSHOT_UPDATER.compareAndSet(this, prev, next)) {
        if (LOG.isDebugEnabled()) {
          String message = "ensureEditableFontPreferences in %s. %s, size: %d".formatted(
            debugSchemeName(),
            editableFontPreferences,
            editableFontPreferences.getSize(editableFontPreferences.getFontFamily())
          );
          Throwable stack = new Throwable(message);
          LOG.debug(message, stack);
        }
        return editorFontView;
      }
    }
  }

  /**
   * Returns the console font preferences of this scheme as a modifiable object.
   * A console that uses the editor font gets a copy of it as its own console font first.
   */
  @NotNull ModifiableFontPreferences ensureEditableConsoleFontPreferences() {
    while (true) {
      ColorsSchemeSnapshot prev = snapshot;
      if (!prev.useEditorFontPreferencesInConsole()) {
        return consoleFontView;
      }
      FontPreferencesSnapshot editableFontPreferences = createEditableCopy(fontPreferencesOf(prev));
      ColorsSchemeSnapshot next = prev
        .withConsoleFontPreferences(editableFontPreferences)
        .withUseEditorFontPreferencesInConsole(false);
      if (SNAPSHOT_UPDATER.compareAndSet(this, prev, next)) {
        return consoleFontView;
      }
    }
  }

  /**
   * Returns the editor font preferences that {@code current} gives.
   * They are the own immutable preferences of the scheme, or a delegate to the application font.
   */
  @NotNull FontPreferences fontPreferencesOf(@NotNull ColorsSchemeSnapshot current) {
    if (current.useAppFontPreferencesInEditor()) {
      return appFontDelegate;
    }
    return current.fontPreferences();
  }

  /**
   * Returns the console font preferences that {@code current} gives.
   * They are the own immutable preferences of the scheme, or a delegate to the editor font.
   */
  private @NotNull FontPreferences consoleFontPreferencesOf(@NotNull ColorsSchemeSnapshot current) {
    if (current.useEditorFontPreferencesInConsole()) {
      return editorFontDelegate;
    }
    return current.consoleFontPreferences();
  }

  /**
   * Returns the console font size that {@code current} gives.
   * Unlike {@link #getConsoleFontSize2D()}, it ignores the application console options and presentation mode.
   */
  float consoleFontSize2D(@NotNull ColorsSchemeSnapshot current) {
    FontPreferences consolePreferences = consoleFontPreferencesOf(current);
    String consoleFontFamily = consolePreferences.getFontFamily();
    if (consolePreferences.hasSize(consoleFontFamily)) {
      return consolePreferences.getSize2D(consoleFontFamily);
    }
    FontPreferences preferences = fontPreferencesOf(current);
    return preferences.getSize2D(preferences.getFontFamily());
  }

  private static @NotNull FontPreferencesSnapshot createEditableCopy(@NotNull FontPreferences preferences) {
    FontPreferencesImpl editableFontPreferences = new FontPreferencesImpl();
    preferences.copyTo(editableFontPreferences);
    return (FontPreferencesSnapshot)editableFontPreferences.getSnapshot();
  }

  private @Nullable String debugSchemeName() {
    try {
      ColorsSchemeSnapshot current = snapshot;
      String schemeName = current.schemeName();
      if (schemeName != null) {
        return schemeName;
      }
      EditorColorsScheme parentScheme = current.parentScheme();
      if (parentScheme == null) {
        return null;
      }
      return parentScheme.getName();
    } catch (Throwable e) {
      LOG.warn("An exception occurred while trying to get scheme name", e);
      return "null(e)";
    }
  }

  protected static boolean colorsEqual(@Nullable Color c1, @Nullable Color c2) {
    if (c1 == NULL_COLOR_MARKER) {
      return c1 == c2;
    }
    return Comparing.equal(c1, c2 == NULL_COLOR_MARKER ? null : c2);
  }

  protected static boolean areDelegatingOrEqual(
    @NotNull FontPreferences preferences1,
    @NotNull FontPreferences preferences2
  ) {
    boolean isDelegating1 = preferences1 instanceof DelegatingFontPreferences;
    boolean isDelegating2 = preferences2 instanceof DelegatingFontPreferences;
    if (isDelegating1 || isDelegating2) {
      return isDelegating1 && isDelegating2;
    }
    return preferences1.equals(preferences2);
  }

  private static boolean hasExplicitlyDefinedAttributes(
    @NotNull EditorColorsScheme scheme,
    @NotNull TextAttributesKey key
  ) {
    TextAttributes directAttrs = scheme instanceof DefaultColorsScheme defaultScheme
                                 ? defaultScheme.getDirectlyDefinedAttributes(key)
                                 : null;
    return directAttrs != null && directAttrs != INHERITED_ATTRS_MARKER;
  }

  private static boolean hasExplicitlyDefinedColors(
    @NotNull EditorColorsScheme scheme,
    @NotNull ColorKey key
  ) {
    Color directColor = scheme instanceof DefaultColorsScheme defaultScheme
                        ? defaultScheme.getDirectlyDefinedColor(key)
                        : null;
    return directColor != null && directColor != INHERITED_COLOR_MARKER;
  }

  private static boolean parentHasSameAttributes(
    @NotNull EditorColorsScheme parentScheme,
    @NotNull String keyName,
    @NotNull TextAttributes attributes
  ) {
    TextAttributesKey key = TextAttributesKey.find(keyName);
    if (attributes == INHERITED_ATTRS_MARKER) {
      return !hasExplicitlyDefinedAttributes(parentScheme, key);
    }
    TextAttributes parent = parentScheme instanceof DefaultColorsScheme defaultScheme
                            ? defaultScheme.getAttributes(key, false)
                            : parentScheme.getAttributes(key);
    return Comparing.equal(parent, attributes);
  }

  private static boolean parentHasSameColor(
    @NotNull EditorColorsScheme parentScheme,
    @NotNull ColorKey key,
    @NotNull Color color
  ) {
    if (color == INHERITED_COLOR_MARKER) {
      return !hasExplicitlyDefinedColors(parentScheme, key);
    }
    Color parent = parentScheme instanceof DefaultColorsScheme defaultScheme
                   ? defaultScheme.getColor(key, false)
                   : parentScheme.getColor(key);
    return Comparing.equal(parent, color == NULL_COLOR_MARKER ? null : color);
  }

  private static @Nullable EditorColorsScheme getBaseDefaultScheme(@NotNull EditorColorsScheme scheme) {
    if (!(scheme instanceof AbstractColorsScheme absScheme)) {
      return null;
    }
    if (scheme instanceof DefaultColorsScheme) {
      return scheme;
    }
    EditorColorsScheme parent = absScheme.snapshot.parentScheme();
    if (parent != null) {
      return getBaseDefaultScheme(parent);
    }
    return null;
  }

  /**
   * Keeps the own editor font of the scheme in the snapshot of the scheme.
   */
  private final class EditorFontStorage implements FontPreferencesImpl.Storage {
    @Override
    public @NotNull FontPreferencesSnapshot get() {
      return snapshot.fontPreferences();
    }

    @Override
    public boolean compareAndSet(@NotNull FontPreferencesSnapshot prev, @NotNull FontPreferencesSnapshot next) {
      ColorsSchemeSnapshot current = snapshot;
      return current.fontPreferences() == prev &&
             SNAPSHOT_UPDATER.compareAndSet(AbstractColorsScheme.this, current, current.withFontPreferences(next));
    }
  }

  /**
   * Keeps the own console font of the scheme in the snapshot of the scheme.
   */
  private final class ConsoleFontStorage implements FontPreferencesImpl.Storage {
    @Override
    public @NotNull FontPreferencesSnapshot get() {
      return snapshot.consoleFontPreferences();
    }

    @Override
    public boolean compareAndSet(@NotNull FontPreferencesSnapshot prev, @NotNull FontPreferencesSnapshot next) {
      ColorsSchemeSnapshot current = snapshot;
      return current.consoleFontPreferences() == prev &&
             SNAPSHOT_UPDATER.compareAndSet(AbstractColorsScheme.this, current, current.withConsoleFontPreferences(next));
    }
  }
}
