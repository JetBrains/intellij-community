// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private final @NotNull RepositoryLibraryDescription libraryDescription;
  private final RepositoryLibraryPropertiesEditor editor;
  private final RepositoryLibraryPropertiesModel model;

  public RepositoryLibrarySupportInModuleConfigurable(@Nullable Project project, @NotNull RepositoryLibraryDescription libraryDescription) {
    this.libraryDescription = libraryDescription;
    RepositoryLibraryProperties defaultProperties = libraryDescription.createDefaultProperties();
    this.model = new RepositoryLibraryPropertiesModel(defaultProperties.getVersion(), false, false, defaultProperties.isIncludeTransitiveDependencies(),
                                                      defaultProperties.getExcludedDependencies());
    editor = new RepositoryLibraryPropertiesEditor(project, model, libraryDescription, false);
  }

  @Override
  public @Nullable JComponent createComponent() {
    return editor.getMainPanel();
  }

  @Override
  public void addSupport(@NotNull Module module,
                         @NotNull ModifiableRootModel rootModel,
                         @NotNull ModifiableModelsProvider modifiableModelsProvider) {
    final RepositoryLibrarySupport librarySupport = new RepositoryLibrarySupport(module.getProject(), libraryDescription, model);
    librarySupport.addSupport(module, rootModel, modifiableModelsProvider, null);
  }
}
