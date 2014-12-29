/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle;

/**
 * Used in settings providers to indicate in which order a settings tab or panel must be shown in Settings UI.
 *
 * @author Rustam Vishnyakov
 */
public enum DisplayPriority {
  /**
   * General settings (topmost)
   */
  GENERAL_SETTINGS,
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
