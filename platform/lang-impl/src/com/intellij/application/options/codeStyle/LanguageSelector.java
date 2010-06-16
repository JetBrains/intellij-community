/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle;

import com.intellij.lang.Language;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.Nullable;

/**
 * Maintains programming language selection for code style panels.
 */
public class LanguageSelector {

  private Language myLanguage;
  private final EventDispatcher<LanguageSelectorListener> myDispatcher = EventDispatcher.create(LanguageSelectorListener.class);
  private Language[] myLanguages = LanguageCodeStyleSettingsProvider.getLanguagesWithCodeStyleSettings(); 

  /**
   * @return The currently selected language.
   */
  @Nullable
  public Language getLanguage() {
    if (myLanguage == null && myLanguages.length > 0) {
      myLanguage = myLanguages[0];
    }
    return myLanguage;
  }

  /**
   * Sets a new language as the current one and notifies the listeners on language change.
   * @param language  The new language to use.
   */
  public void setLanguage(Language language) {
    myLanguage = language;
    notifyListeners(language);
  }

  /**
   * Adds a language selection listener.
   * @param l The listener to add.
   */
  public void addListener(LanguageSelectorListener l) {
    myDispatcher.addListener(l);
  }

  public void removeListener(LanguageSelectorListener l) {
    myDispatcher.removeListener(l);
  }

  private void notifyListeners(Language lang) {
    myDispatcher.getMulticaster().languageChanged(lang);
  }
}
