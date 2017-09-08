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
package com.intellij.jarRepository;

import com.intellij.framework.addSupport.FrameworkSupportInModuleConfigurable;
import com.intellij.jarRepository.settings.RepositoryLibraryPropertiesEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;
import org.jetbrains.idea.maven.utils.library.RepositoryLibrarySupport;
import org.jetbrains.idea.maven.utils.library.propertiesEditor.RepositoryLibraryPropertiesModel;

import javax.swing.*;

public class RepositoryLibrarySupportInModuleConfigurable extends FrameworkSupportInModuleConfigurable {
  @NotNull private final RepositoryLibraryDescription libraryDescription;
  private RepositoryLibraryPropertiesEditor editor;
  private RepositoryLibraryPropertiesModel model;

  public RepositoryLibrarySupportInModuleConfigurable(@Nullable Project project, @NotNull RepositoryLibraryDescription libraryDescription) {
    this.libraryDescription = libraryDescription;
    RepositoryLibraryProperties defaultProperties = libraryDescription.createDefaultProperties();
    this.model = new RepositoryLibraryPropertiesModel(defaultProperties.getVersion(), false, false, defaultProperties.isIncludeTransitiveDependencies());
    editor = new RepositoryLibraryPropertiesEditor(project, model, libraryDescription);
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return editor.getMainPanel();
  }

  @Override
  public void addSupport(@NotNull Module module,
                         @NotNull ModifiableRootModel rootModel,
                         @NotNull ModifiableModelsProvider modifiableModelsProvider) {
    final RepositoryLibrarySupport librarySupport = new RepositoryLibrarySupport(module.getProject(), libraryDescription, model);
    librarySupport.addSupport(module, rootModel, modifiableModelsProvider);
  }
}
