/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.fileTemplates.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.ProjectManager;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
@State(
  name = "ExportableFileTemplateSettings",
  storages = @Storage(FileTemplateSettings.EXPORTABLE_SETTINGS_FILE),
  additionalExportFile = FileTemplatesLoader.TEMPLATES_DIR
)
public class ExportableFileTemplateSettings implements PersistentStateComponent<Element> {
  @Nullable
  @Override
  public Element getState() {
    return getSettingInstance().getState();
  }

  @Override
  public void loadState(Element state) {
    getSettingInstance().loadState(state);
  }

  private static FileTemplateSettings getSettingInstance() {
    return FileTemplateSettings.getInstance(ProjectManager.getInstance().getDefaultProject());
  }
}
