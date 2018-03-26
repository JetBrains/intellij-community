/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.lang.IdeLanguageCustomization;
import com.intellij.lang.Language;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
public abstract class CodeStyleSettingsProvider {
  public static final ExtensionPointName<CodeStyleSettingsProvider> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.codeStyleSettingsProvider");


  @Nullable
  public CustomCodeStyleSettings createCustomSettings(CodeStyleSettings settings) {
    return null;
  }

  @NotNull
  public abstract Configurable createSettingsPage(CodeStyleSettings settings, final CodeStyleSettings modelSettings);

  /**
   * Returns the name of the configurable page without creating a Configurable instance.
   *
   * @return the display name of the configurable page.
   * @since 9.0
   */
  @Nullable
  public String getConfigurableDisplayName() {
    Language lang = getLanguage();
    return lang == null ? null : lang.getDisplayName();
  }

  public boolean hasSettingsPage() {
    return true;
  }

  public DisplayPriority getPriority() {
    List<Language> primaryIdeLanguages = IdeLanguageCustomization.getInstance().getPrimaryIdeLanguages();
    return primaryIdeLanguages.contains(getLanguage()) ? DisplayPriority.KEY_LANGUAGE_SETTINGS : DisplayPriority.LANGUAGE_SETTINGS;
  }

  /**
   * Specifies a language this provider applies to. If the language is not null, its display name will
   * be used as a configurable name by default if {@code getConfigurableDisplayName()} is not
   * overridden.
   * 
   * @return null by default.
   */
  @Nullable
  public Language getLanguage() {
    return null;
  }

}
