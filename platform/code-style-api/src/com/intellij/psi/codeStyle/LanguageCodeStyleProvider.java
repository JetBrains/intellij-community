// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.lang.Language;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static java.lang.Character.isLetterOrDigit;

/**
 * Can be obtained via {@link CodeStyleSettingsService#getLanguageCodeStyleProvider(Language)}
 */
@ApiStatus.Internal
public interface LanguageCodeStyleProvider extends CustomCodeStyleSettingsFactory {
  @NotNull
  Language getLanguage();

  @NotNull
  CommonCodeStyleSettings getDefaultCommonSettings();

  @NotNull
  DocCommentSettings getDocCommentSettings(@NotNull CodeStyleSettings rootSettings);

  Set<String> getSupportedFields();

  /**
   * Return true if formatter for this language uses {@link CommonCodeStyleSettings#KEEP_LINE_BREAKS} flag
   * for custom line breaks processing
   */
  @ApiStatus.Experimental
  default boolean usesCommonKeepLineBreaks() {
    return false;
  }
}
