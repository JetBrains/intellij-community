// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.DelegatingFontPreferences;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontCache;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.impl.FontFamilyService;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JdkConstants.FontStyle;
import com.jetbrains.JBR;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Font;
import java.awt.font.TextAttribute;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Supplier;

/**
 * Builds and keeps the editor and console fonts of one scheme.
 * The application service {@link GlobalSchemeFontCache} and each {@link AbstractColorsScheme} hold an instance.
 */
@ApiStatus.Internal
public final class EditorFontCacheImpl extends EditorFontCache {
  private static final Logger LOG = Logger.getInstance(EditorFontCacheImpl.class);
  private static final Map<TextAttribute, Integer> LIGATURES_ATTRIBUTES = Map.of(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON);
  private static final AtomicReferenceFieldUpdater<EditorFontCacheImpl, Font[]> FONTS_UPDATER =
    AtomicReferenceFieldUpdater.newUpdater(EditorFontCacheImpl.class, Font[].class, "fonts");

  public static @NotNull Font deriveFontWithLigatures(@NotNull Font font, boolean enableLigatures) {
    return enableLigatures ? font.deriveFont(LIGATURES_ATTRIBUTES) : font;
  }

  private final @NotNull Supplier<? extends EditorColorsScheme> schemeSupplier;
  private volatile Font @NotNull [] fonts = new Font[0];

  public EditorFontCacheImpl(@NotNull Supplier<? extends EditorColorsScheme> schemeSupplier) {
    this.schemeSupplier = schemeSupplier;
  }

  @Override
  public @NotNull Font getFont(@Nullable EditorFontType key) {
    Font[] fonts = this.fonts;
    if (fonts.length == 0) {
      Font[] newFonts = initFonts();
      FONTS_UPDATER.compareAndSet(this, fonts, newFonts);
      fonts = newFonts;
    }
    EditorFontType fontType = Objects.requireNonNullElse(key, EditorFontType.PLAIN);
    Font font = fonts[fontType.ordinal()];
    assert font != null : "Font " + fontType + " not found.";
    UISettings uiSettings = UISettings.getInstance();
    if (uiSettings.getPresentationMode()) {
      return font.deriveFont(UISettingsUtils.with(uiSettings).getPresentationModeFontSize());
    }
    return font;
  }

  @Override
  public void reset() {
    FONTS_UPDATER.set(this, new Font[0]);
  }

  /**
   * Builds the fonts from two snapshots, so the editor fonts come from one version of their preferences.
   * The console fonts also come from one version of their preferences.
   */
  private Font @NotNull [] initFonts() {
    EditorColorsScheme scheme = schemeSupplier.get();
    FontPreferences schemePreferences = scheme.getFontPreferences();
    FontPreferences preferences = schemePreferences.getSnapshot();
    FontPreferences consolePreferences = scheme.getConsoleFontPreferences().getSnapshot();
    String editorFontName = getEditorFontName(preferences);
    String consoleFontName = consolePreferences.getFontFamily();
    float editorFontSize = preferences.getSize2D(preferences.getFontFamily());
    float consoleFontSize = getConsoleFontSize(consolePreferences, consoleFontName, editorFontSize);

    if (LOG.isDebugEnabled()) {
      logDebug(scheme, schemePreferences instanceof DelegatingFontPreferences, editorFontName, editorFontSize);
    }

    assert EditorFontType.values().length == 8;

    return new Font[] {
      getFont(editorFontName, Font.PLAIN, editorFontSize, preferences), // EditorFontType.PLAIN
      getFont(editorFontName, Font.BOLD, editorFontSize, preferences), // EditorFontType.BOLD
      getFont(editorFontName, Font.ITALIC, editorFontSize, preferences), // EditorFontType.ITALIC
      getFont(editorFontName, Font.BOLD | Font.ITALIC, editorFontSize, preferences), // EditorFontType.BOLD_ITALIC
      getFont(consoleFontName, Font.PLAIN, consoleFontSize, consolePreferences), // EditorFontType.CONSOLE_PLAIN
      getFont(consoleFontName, Font.BOLD, consoleFontSize, consolePreferences), // EditorFontType.CONSOLE_BOLD
      getFont(consoleFontName, Font.ITALIC, consoleFontSize, consolePreferences), // EditorFontType.CONSOLE_ITALIC
      getFont(consoleFontName, Font.BOLD | Font.ITALIC, consoleFontSize, consolePreferences) // EditorFontType.CONSOLE_BOLD_ITALIC
    };
  }

  private static Font getFont(
    @NotNull String familyName,
    @FontStyle int style,
    float fontSize,
    @NotNull FontPreferences fontPreferences
  ) {
    Font editorFont = FontFamilyService.getFont(
      familyName,
      fontPreferences.getRegularSubFamily(),
      fontPreferences.getBoldSubFamily(),
      style,
      fontSize
    );
    editorFont = deriveFontWithLigatures(editorFont, fontPreferences.useLigatures());
    if (!fontPreferences.getCharacterVariants().isEmpty()) {
      editorFont = JBR.getFontExtensions().deriveFontWithFeatures(
        editorFont,
        ArrayUtil.toStringArray(fontPreferences.getCharacterVariants())
      );
    }
    return editorFont;
  }

  private static @NotNull String getEditorFontName(@NotNull FontPreferences preferences) {
    String editorFontName = preferences.getFontFamily();
    String fallbackName = getFallbackName(editorFontName);
    if (fallbackName != null) {
      return fallbackName;
    }
    return editorFontName;
  }

  /**
   * Returns the console font size like {@link AbstractColorsScheme#getConsoleFontSize2D()} outside presentation mode.
   * The build ignores presentation mode, because {@link #getFont(EditorFontType)} sets the presentation size in that mode.
   */
  private static float getConsoleFontSize(@NotNull FontPreferences consolePreferences, @NotNull String consoleFontName, float editorFontSize) {
    if (consolePreferences.hasSize(consoleFontName)) {
      return consolePreferences.getSize2D(consoleFontName);
    }
    return editorFontSize;
  }

  private static @Nullable String getFallbackName(@NotNull String fontName) {
    Font plainFont = new Font(fontName, Font.PLAIN, 12);
    boolean isDialogFont = plainFont.getFamily().equals("Dialog");
    if (isDialogFont && !("Dialog".equals(fontName) || fontName.startsWith("Dialog."))) {
      FontPreferences preferences = AppEditorFontOptions.getInstance().getFontPreferences();
      return preferences.getFontFamily();
    }
    return null;
  }

  private static void logDebug(EditorColorsScheme scheme, boolean delegating, String editorFontName, float editorFontSize) {
    String schemeName;
    try {
      schemeName = scheme.getName();
    } catch (Throwable th) {
      LOG.warn(th);
      schemeName = "unknown(th)";
    }
    LOG.debug(String.format(
      "Initializing fonts: scheme=%s, delegating=%b, fontName=%s, fontSize=%.2f",
      schemeName, delegating, editorFontName, editorFontSize
    ));
  }
}
