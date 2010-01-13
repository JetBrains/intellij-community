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

package com.intellij.application.options.editor;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.TabbedConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class AutoImportOptionsConfigurable extends TabbedConfigurable<AutoImportOptionsProvider> implements EditorOptionsProvider {

  public AutoImportOptionsConfigurable(@NotNull Application application) {
    super(application);
  }

  protected List<AutoImportOptionsProvider> createConfigurables() {
    return Arrays.asList(Extensions.getExtensions(AutoImportOptionsProvider.EP_NAME));
  }

  @Nls
  public String getDisplayName() {
    return ApplicationBundle.message("auto.import");
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return "reference.settingsdialog.IDE.editor.autoimport";
  }

  public String getId() {
    return "editor.preferences.import";
  }

  public Runnable enableSearch(final String option) {
    return null;
  }
}
