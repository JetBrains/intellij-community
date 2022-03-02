// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.application.options.EditorFontsConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.util.ui.FontInfo;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Standard Java font API doesn't allow accessing fonts by their typographic family/subfamily names
 * (see https://docs.microsoft.com/en-us/typography/opentype/spec/name#name-ids). This service allows to fill this gap,
 * if the runtime in use provides required support (see {@link #isServiceSupported()}).
 */
public class FontFamilyService {
  /**
   * Tells whether the runtime being used provides the ability to access fonts, available to the runtime, by their
   * typographic family/subfamily names. Latest version of JetBrains Runtime is supposed to provide such support.
   * If such support isn't available, the rest of methods in this class will operate in a 'fallback' mode, using standard Java API
   * (using usual family/subfamily names instead of typographic ones).
   */
  public static boolean isServiceSupported() {
    return getInstance().isSupportedImpl();
  }

  /**
   * Returns typographic family names (in alphabetical order) for fonts available to the runtime environment.
   */
  public static @NotNull List<String> getAvailableFamilies() {
    return getInstance().getAvailableFamiliesImpl();
  }

  /**
   * Tells whether given font family is monospaced. This property is assumed to be applicable for the whole family, so the implementation
   * usually performs the check only for a single font from the family.
   */
  public static boolean isMonospaced(@NotNull String family) {
    return getInstance().isMonospacedImpl(family);
  }

  /**
   * Returns subfamilies available in the given typographic family. This doesn't include italic faces (unless there are no 'normal' faces in
   * the family). The results are sorted by font weight (ascending, light to bold).
   */
  public static @NotNull List<@NotNull String> getSubFamilies(@NotNull String family) {
    return getInstance().getSubFamiliesImpl(family);
  }

  /**
   * Returns subfamily that should be used for 'normal' text, if a user didn't express any preferences.
   */
  public static @NotNull String getRecommendedSubFamily(@NotNull String family) {
    return getInstance().getRecommendedSubFamilyImpl(family);
  }

  /**
   * Returns subfamily that should be used for 'bold' text, if specified subfamily is selected for displaying 'normal' text.
   */
  public static @NotNull String getRecommendedBoldSubFamily(@NotNull String family, @NotNull String mainSubFamily) {
    return getInstance().getRecommendedBoldSubFamilyImpl(family, mainSubFamily);
  }

  /**
   * Given the typographic family name and (optionally) the subfamily names to be used for 'normal' and 'bold' text, selected by a user,
   * returns font instance, representing given Java font style (plain, bold, italic or bold italic). Size of returned font isn't specified.
   */
  public static @NotNull Font getFont(@NotNull String family,
                                      @Nullable String regularSubFamily,
                                      @Nullable String boldSubFamily,
                                      @JdkConstants.FontStyle int style) {
    return getInstance().getFontImpl(family, regularSubFamily, boldSubFamily, style);
  }

  /**
   * Same as {@link #getFont(String, String, String, int)}, but also allows to specify required font size.
   */
  public static @NotNull Font getFont(@NotNull String family,
                                      @Nullable String regularSubFamily,
                                      @Nullable String boldSubFamily,
                                      @JdkConstants.FontStyle int style,
                                      int size) {
    return getFont(family, regularSubFamily, boldSubFamily, style, (float)size);
  }

  /**
   * Floating-point version of {@link #getFont(String, String, String, int, int)}
   */
  public static @NotNull Font getFont(@NotNull String family,
                                      @Nullable String regularSubFamily,
                                      @Nullable String boldSubFamily,
                                      @JdkConstants.FontStyle int style,
                                      float size) {
    return getFont(family, regularSubFamily, boldSubFamily, style).deriveFont(size);
  }

  /**
   * Returns font with given typographic family/subfamily and size. This is an alternative to {@link Font#Font(String, int, int)},
   * allowing to use typographic family/subfamily instead of 'standard' family and Java font style.
   */
  public static @NotNull Font getFont(@NotNull String family, @NotNull String subFamily, int size) {
    return getFont(family, subFamily, subFamily, Font.PLAIN, size);
  }

  /**
   * Floating-point version of {@link #getFont(String, String, int)}
   */
  public static @NotNull Font getFont(@NotNull String family, @NotNull String subFamily, float size) {
    return getFont(family, subFamily, subFamily, Font.PLAIN, size);
  }

  /**
   * Migrates from old font setting ('standard' font family name) to new font settings (typographic family name and typographic subfamily
   * names to be used for 'normal' and 'bold' font) if possible.
   *
   * @return array of 3 elements: typographic family name, typographic subfamily for 'normal' text and typographic subfamily for 'bold' text
   */
  public static String @NotNull [] migrateFontSetting(@NotNull String family) {
    return getInstance().migrateFontSettingImpl(family);
  }

  private static @NotNull FontFamilyService getInstance() {
    FontFamilyService instance = ApplicationManager.getApplication().getService(FontFamilyService.class);
    return instance == null ? new FontFamilyService() : instance;
  }

  private static final String MAIN_FALLBACK_SUB_FAMILY = "Regular";
  private static final String BOLD_FALLBACK_SUB_FAMILY = "Bold";

  protected boolean isSupportedImpl() {
    return false;
  }

  protected @NotNull List<String> getAvailableFamiliesImpl() {
    return Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ENGLISH));
  }

  protected boolean isMonospacedImpl(@NotNull String family) {
    return FontInfo.isMonospacedWithStyles(new Font(family, Font.PLAIN, EditorFontsConstants.getDefaultEditorFontSize()));
  }

  protected @NotNull List<@NotNull String> getSubFamiliesImpl(@NotNull String family) {
    return Arrays.asList(MAIN_FALLBACK_SUB_FAMILY, BOLD_FALLBACK_SUB_FAMILY);
  }

  protected @NotNull String getRecommendedSubFamilyImpl(@NotNull String family) {
    return MAIN_FALLBACK_SUB_FAMILY;
  }

  protected @NotNull String getRecommendedBoldSubFamilyImpl(@NotNull String family, @NotNull String mainSubFamily) {
    return BOLD_FALLBACK_SUB_FAMILY;
  }

  protected @NotNull Font getFontImpl(@NotNull String family,
                                      @Nullable String regularSubFamily,
                                      @Nullable String boldSubFamily,
                                      @JdkConstants.FontStyle int style) {
    Font font = new Font(family, style, 1);
    if (font.getFamily().equals(Font.DIALOG) && !Font.DIALOG.equals(family)) {
      // requested family isn't available
      return new Font(FontPreferences.DEFAULT_FONT_NAME, style, 1);
    }
    return font;
  }

  protected String @NotNull [] migrateFontSettingImpl(@NotNull String family) {
    return new String[] {family, null, null};
  }
}
