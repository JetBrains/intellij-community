// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

/**
 * Used in settings providers to indicate in which order a settings tab or panel must be shown in Settings UI.
 */
public enum DisplayPriority {
  /**
   * General settings (topmost)
   */
  GENERAL_SETTINGS,
  /**
   * Font settings
   */
  FONT_SETTINGS,
  /**
   * Any generic settings normally used by multiple languages.
   */
  COMMON_SETTINGS,
  /**
   * Code arrangement settings like imports, etc.
   */
  CODE_SETTINGS,
  /**
   * Key IDE language priority (depends on product), for example, Java for IntelliJ IDEA, PHP for PhpStorm etc.
   */
  KEY_LANGUAGE_SETTINGS,
  /**
   * Language-specific settings.
   */
  LANGUAGE_SETTINGS,
  /**
   * Any other settings.
   */
  OTHER_SETTINGS
}
