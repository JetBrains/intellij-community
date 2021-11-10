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
package com.intellij.application.options;

import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Implement this provider if you want to add a configuration section to the root panel at Settings|Editor|Code Style. Create and return
 * the configuration component to be added at the bottom of the root "Code Style" panel in {@link #createComponent()} method. Declare the
 * extension in {@code plugin.xml} as follows:
 * <pre><code>
 * &lt;generalCodeStyleOptionsProvider instance="com.company.MyProvider"/&gt;
 * </code></pre>
 */
public interface GeneralCodeStyleOptionsProvider extends UnnamedConfigurable {
  /**
   * Apply the currently set UI options to the given code style settings.
   *
   * @param settings The settings to apply the UI options to.
   */
  void apply(@NotNull CodeStyleSettings settings);

  /**
   * Check if the currently set UI options differ from the ones in the given code style settings.
   *
   * @param settings The settings to compare with.
   *
   * @return True if the settings do not match, false if the UI options are the same as in {@code settings}.
   */
  boolean isModified(@NotNull CodeStyleSettings settings);

  /**
   * Update the UI controls from the given code style settings.
   *
   * @param settings The settings to take the updated values from.
   */
  void reset(@NotNull CodeStyleSettings settings);
}
