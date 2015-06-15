/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ui.tabs;

import com.intellij.ide.ui.AppearanceOptionsTopHitProvider;
import com.intellij.ide.ui.OptionsTopHitProvider;
import com.intellij.ide.ui.PublicMethodBasedOptionDescription;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.project.Project;
import com.intellij.ui.FileColorManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Sergey.Malenkov
 */
public final class FileColorsOptionsTopHitProvider extends OptionsTopHitProvider {
  @Override
  public String getId() {
    return AppearanceOptionsTopHitProvider.ID;
  }

  @NotNull
  @Override
  public Collection<BooleanOptionDescription> getOptions(@Nullable Project project) {
    if (project != null) {
      FileColorManager manager = FileColorManager.getInstance(project);
      if (manager != null) {
        BooleanOptionDescription enabled = new Option(manager, "File Colors enabled", "isEnabled", "setEnabled");
        return !enabled.isOptionEnabled()
               ? Collections.singletonList(enabled)
               : Collections.unmodifiableCollection(Arrays.asList(
                 enabled,
                 new Option(manager, "Use File Colors in Editor Tabs", "isEnabledForTabs", "setEnabledForTabs"),
                 new Option(manager, "Use File Colors in Project View", "isEnabledForProjectView", "setEnabledForProjectView")));
      }
    }
    return Collections.emptyList();
  }

  private static class Option extends PublicMethodBasedOptionDescription {
    private final FileColorManager myManager;

    public Option(FileColorManager manager, String option, String getter, String setter) {
      super(option, "reference.settings.ide.settings.file-colors", getter, setter);
      myManager = manager;
    }

    @Override
    public Object getInstance() {
      return myManager;
    }

    @Override
    protected void fireUpdated() {
      UISettings.getInstance().fireUISettingsChanged();
    }
  }
}
