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

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class CodeStyleSettingsProvider {
  public static final ExtensionPointName<CodeStyleSettingsProvider> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.codeStyleSettingsProvider");

  public final static int GENERAL_PRIORITY          = 0;
  public final static int COMMON_SETTINGS_PRIORITY  = 1;
  public final static int CODE_PRIORITY             = 2;
  public final static int LANGUAGE_PRIORITY         = 3;
  public final static int OTHER_PRIORITY            = 4;

  @Nullable
  public CustomCodeStyleSettings createCustomSettings(CodeStyleSettings settings) {
    return null;
  }

  @NotNull
  public abstract Configurable createSettingsPage(CodeStyleSettings settings, final CodeStyleSettings originalSettings);

  /**
   * Returns the name of the configurable page without creating a Configurable instance.
   *
   * @return the display name of the configurable page.
   * @since 9.0
   */
  @Nullable
  public String getConfigurableDisplayName() {
    return null;
  }

  public boolean hasSettingsPage() {
    return true;
  }

  public int getPriority() {
    return LANGUAGE_PRIORITY;
  }
}
