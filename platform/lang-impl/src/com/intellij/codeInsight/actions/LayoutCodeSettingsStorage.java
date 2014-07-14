/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.actions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class LayoutCodeSettingsStorage {

  private LayoutCodeSettingsStorage() {
  }

  public static void saveRearrangeEntriesOptionFor(@NotNull Project project, @NotNull Language language, boolean value) {
    String key = getRearrangeEntriesKeyForLanguage(language);
    PropertiesComponent.getInstance(project).setValue(key, Boolean.toString(value));
  }

  public static void saveRearrangeEntriesOptionFor(@NotNull Project project, boolean value) {
    PropertiesComponent.getInstance(project).setValue(LayoutCodeConstants.REARRANGE_ENTRIES_KEY, Boolean.toString(value));
  }

  public static boolean getLastSavedRearrangeEntriesCbStateFor(@NotNull Project project) {
    return PropertiesComponent.getInstance(project).getBoolean(LayoutCodeConstants.REARRANGE_ENTRIES_KEY, false);
  }

  public static boolean getLastSavedRearrangeEntriesCbStateFor(@NotNull Project project, @NotNull Language language) {
    String key = getRearrangeEntriesKeyForLanguage(language);
    return PropertiesComponent.getInstance(project).getBoolean(key, false);
  }

  private static String getRearrangeEntriesKeyForLanguage(@NotNull Language language) {
    return LayoutCodeConstants.REARRANGE_ENTRIES_KEY + language.getDisplayName();
  }

}
