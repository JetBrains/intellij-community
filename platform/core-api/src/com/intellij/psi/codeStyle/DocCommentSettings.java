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
package com.intellij.psi.codeStyle;

/**
 * A set of code documentation comment settings if supported by a language used in common doc comment handling algorithms.
 */
public interface DocCommentSettings {
  /**
   * Default doc comment settings if not provided by {@code LanguageCodeStyleSettingsProvider}
   */
  DocCommentSettings DEFAULTS = new Defaults();

  /**
   * @return True if doc comment formatting enabled.
   */
  boolean isDocFormattingEnabled();

  /**
   * Enable or disable doc comment formatting.
   * @param formattingEnabled The enable/disable flag.
   */
  void setDocFormattingEnabled(boolean formattingEnabled);

  /**
   * @return True if a leading asterisk '*' should be inserted on a new comment line.
   */
  boolean isLeadingAsteriskEnabled();

  final class Defaults implements DocCommentSettings {

    @Override
    public boolean isDocFormattingEnabled() {
      return true;
    }

    @Override
    public void setDocFormattingEnabled(boolean formattingEnabled) {
    }

    @Override
    public boolean isLeadingAsteriskEnabled() {
      return true;
    }
  }
}
