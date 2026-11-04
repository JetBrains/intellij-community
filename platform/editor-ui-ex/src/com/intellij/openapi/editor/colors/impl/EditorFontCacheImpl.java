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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Font;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Supplier;

import static com.intellij.diagnostic.ControlFlowExceptionsKt.rethrowControlFlowException;

/**
 * Builds and keeps the editor and console fonts of one scheme.
 * The fonts follow the font preferences of the scheme: a call that finds other preference snapshots builds the fonts again.
 * A call for an editor font type checks only the editor preferences, because the editor fonts do not depend on the console preferences.
 * A {@link #reset()} makes the next call build the fonts again, for a change outside the font preferences.
 * The application service {@link GlobalSchemeFontCache} and each {@link AbstractColorsScheme} hold an instance.
 */
@ApiStatus.Internal
public final class EditorFontCacheImpl extends EditorFontCache {
  private static final Logger LOG = Logger.getInstance(EditorFontCacheImpl.class);
  private static final AtomicReferenceFieldUpdater<EditorFontCacheImpl, EditorFonts> FONTS_UPDATER =
    AtomicReferenceFieldUpdater.newUpdater(EditorFontCacheImpl.class, EditorFonts.class, "fonts");

  private final @NotNull Supplier<? extends EditorColorsScheme> schemeSupplier;
  private volatile @NotNull EditorFonts fonts = EditorFonts.empty();

  public EditorFontCacheImpl(@NotNull Supplier<? extends EditorColorsScheme> schemeSupplier) {
    this.schemeSupplier = schemeSupplier;
  }

  @Override
  public @NotNull Font getFont(@Nullable EditorFontType key) {
    EditorFontType fontType = Objects.requireNonNullElse(key, EditorFontType.PLAIN);
    EditorFonts fonts = this.fonts;
    EditorColorsScheme scheme = schemeSupplier.get();
    FontPreferences schemePreferences = scheme.getFontPreferences();
    FontPreferences preferences = schemePreferences.getSnapshot();
    boolean builtFromPreferences = isConsoleType(fontType)
                                   ? fonts.isBuiltFrom(preferences, scheme.getConsoleFontPreferences().getSnapshot())
                                   : fonts.isBuiltFromEditorFont(preferences);
    if (!builtFromPreferences) {
      FontPreferences consolePreferences = scheme.getConsoleFontPreferences().getSnapshot();
      boolean delegating = schemePreferences instanceof DelegatingFontPreferences;
      EditorFonts newFonts = buildFonts(scheme, delegating, preferences, consolePreferences);
      FONTS_UPDATER.compareAndSet(this, fonts, newFonts);
      fonts = newFonts;
    }
    UISettings uiSettings = UISettings.getInstance();
    if (uiSettings.getPresentationMode()) {
      float size = UISettingsUtils.with(uiSettings).getPresentationModeFontSize();
      return fonts.withSize(size).get(fontType);
    }
    return fonts.get(fontType);
  }

  @Override
  public void reset() {
    fonts = EditorFonts.empty();
  }

  /**
   * Builds the fonts from two snapshots, so the editor fonts come from one version of their preferences.
   * The console fonts also come from one version of their preferences.
   * The build ignores presentation mode, because {@link #getFont(EditorFontType)} sets the presentation size in that mode.
   */
  private static @NotNull EditorFonts buildFonts(
    @NotNull EditorColorsScheme scheme,
    boolean delegating,
    @NotNull FontPreferences preferences,
    @NotNull FontPreferences consolePreferences
  ) {
    String editorFontName = getEditorFontName(preferences);
    String consoleFontName = consolePreferences.getFontFamily();
    float editorFontSize = preferences.getSize2D(preferences.getFontFamily());
    float consoleFontSize = AbstractColorsScheme.consoleFontSize2D(consolePreferences, editorFontSize);
    if (LOG.isDebugEnabled()) {
      logDebug(scheme, delegating, editorFontName, editorFontSize);
    }
    return EditorFonts.empty().with(
      editorFontName,
      editorFontSize,
      preferences,
      consoleFontName,
      consoleFontSize,
      consolePreferences,
      null
    );
  }

  private static boolean isConsoleType(@NotNull EditorFontType fontType) {
    return fontType == EditorFontType.CONSOLE_PLAIN ||
           fontType == EditorFontType.CONSOLE_BOLD ||
           fontType == EditorFontType.CONSOLE_ITALIC ||
           fontType == EditorFontType.CONSOLE_BOLD_ITALIC;
  }

  private static @NotNull String getEditorFontName(@NotNull FontPreferences preferences) {
    String editorFontName = preferences.getFontFamily();
    String fallbackName = getFallbackName(editorFontName);
    if (fallbackName != null) {
      return fallbackName;
    }
    return editorFontName;
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
      rethrowControlFlowException(th);
      LOG.warn(th);
      schemeName = "unknown(th)";
    }
    LOG.debug(String.format(
      "Initializing fonts: scheme=%s, delegating=%b, fontName=%s, fontSize=%.2f",
      schemeName, delegating, editorFontName, editorFontSize
    ));
  }
}
