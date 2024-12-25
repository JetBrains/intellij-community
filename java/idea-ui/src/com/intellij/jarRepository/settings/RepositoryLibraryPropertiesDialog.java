// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository.settings;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription;
import org.jetbrains.idea.maven.utils.library.propertiesEditor.RepositoryLibraryPropertiesModel;

import javax.swing.*;

public class RepositoryLibraryPropertiesDialog extends DialogWrapper {
  private final RepositoryLibraryPropertiesEditor propertiesEditor;
  private final RepositoryLibraryPropertiesModel model;

  public RepositoryLibraryPropertiesDialog(@Nullable Project project,
                                           RepositoryLibraryPropertiesModel model,
                                           RepositoryLibraryDescription description,
                                           final boolean changesRequired, final boolean allowExcludingTransitiveDependencies) {
    this(project, model, description, changesRequired, allowExcludingTransitiveDependencies, false);
  }

  public RepositoryLibraryPropertiesDialog(@Nullable Project project,
                                           RepositoryLibraryPropertiesModel model,
                                           RepositoryLibraryDescription description,
                                           final boolean changesRequired, final boolean allowExcludingTransitiveDependencies,
                                           final boolean globalLibrary) {
    super(project);
    this.model = model;
    propertiesEditor =
      new RepositoryLibraryPropertiesEditor(project, model, description, allowExcludingTransitiveDependencies, new RepositoryLibraryPropertiesEditor.ModelChangeListener() {
        @Override
        public void onChange(@NotNull RepositoryLibraryPropertiesEditor editor) {
          setOKActionEnabled(editor.isValid() && (!changesRequired || editor.hasChanges()));
        }
      }, globalLibrary);
    setTitle(description.getDisplayName());
    init();
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return propertiesEditor.getMainPanel();
  }
}
