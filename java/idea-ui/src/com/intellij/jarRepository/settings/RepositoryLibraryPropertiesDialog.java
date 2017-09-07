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
package com.intellij.jarRepository.settings;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription;
import org.jetbrains.idea.maven.utils.library.propertiesEditor.RepositoryLibraryPropertiesModel;

import javax.swing.*;

public class RepositoryLibraryPropertiesDialog extends DialogWrapper {
  private RepositoryLibraryPropertiesEditor propertiesEditor;
  private RepositoryLibraryPropertiesModel model;

  public RepositoryLibraryPropertiesDialog(@Nullable Project project,
                                           RepositoryLibraryPropertiesModel model,
                                           RepositoryLibraryDescription description,
                                           final boolean changesRequired, final boolean allowExcludingTransitiveDependencies) {
    super(project);
    this.model = model;
    propertiesEditor =
      new RepositoryLibraryPropertiesEditor(project, model, description, allowExcludingTransitiveDependencies, new RepositoryLibraryPropertiesEditor.ModelChangeListener() {
        @Override
        public void onChange(RepositoryLibraryPropertiesEditor editor) {
          setOKActionEnabled(editor.isValid() && (!changesRequired || editor.hasChanges()));
        }
      });
    setTitle(description.getDisplayName());
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return propertiesEditor.getMainPanel();
  }
}
