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
package com.intellij.ide.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.NlsContexts.Label;
import org.jetbrains.annotations.ApiStatus;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public class EditorOptionDescription extends PublicFieldBasedOptionDescription {
  public EditorOptionDescription(String fieldName, @Label String option, String configurableId) {
    super(option, configurableId, fieldName);
  }

  @Override
  public Object getInstance() {
    return EditorSettingsExternalizable.getInstance().getOptions();
  }

  @Override
  protected void fireUpdated() {
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      DaemonCodeAnalyzer.getInstance(project).settingsChanged();
    }

    EditorFactory.getInstance().refreshAllEditors();
  }
}
