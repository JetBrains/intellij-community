// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent;
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor;
import com.intellij.openapi.roots.libraries.ui.LibraryRootsComponentDescriptor;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.workspace.jps.entities.LibraryTypeId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;

import javax.swing.*;

public class RepositoryLibraryType extends LibraryType<RepositoryLibraryProperties> {
  public static final LibraryTypeId LIBRARY_TYPE_ID = new LibraryTypeId("repository");

  public static final PersistentLibraryKind<RepositoryLibraryProperties>
    REPOSITORY_LIBRARY_KIND = new PersistentLibraryKind<>("repository") {
    @Override
    public @NotNull RepositoryLibraryProperties createDefaultProperties() {
      return new RepositoryLibraryProperties();
    }
  };

  protected RepositoryLibraryType() {
    super(REPOSITORY_LIBRARY_KIND);
  }

  public static RepositoryLibraryType getInstance() {
    return EP_NAME.findExtension(RepositoryLibraryType.class);
  }

  @Override
  public @Nullable @NlsContexts.Label String getCreateActionName() {
    return JavaUiBundle.message("repository.library.type.action.name.label");
  }

  @Override
  public NewLibraryConfiguration createNewLibrary(@NotNull JComponent parentComponent,
                                                  @Nullable VirtualFile contextDirectory,
                                                  @NotNull Project project) {
    return JarRepositoryManager.chooseLibraryAndDownload(project, null, parentComponent);
  }

  @Override
  public LibraryPropertiesEditor createPropertiesEditor(@NotNull LibraryEditorComponent<RepositoryLibraryProperties> component) {
    return new RepositoryLibraryWithDescriptionEditor(component);
  }

  @Override
  public @Nullable Icon getIcon(@Nullable RepositoryLibraryProperties properties) {
    if (properties == null || properties.getGroupId() == null || properties.getArtifactId() == null) {
      return RepositoryLibraryDescription.DEFAULT_ICON;
    }
    return RepositoryLibraryDescription.findDescription(properties).getIcon();
  }

  @Override
  public @NotNull String getDescription(@NotNull RepositoryLibraryProperties properties) {
    RepositoryLibraryDescription description = RepositoryLibraryDescription.findDescription(properties);
    final String name = description.getDisplayName(properties.getVersion());
    return JavaUiBundle.message("repository.library.type.maven.description", name);
  }

  @Override
  public @Nullable LibraryRootsComponentDescriptor createLibraryRootsComponentDescriptor() {
    return new RepositoryLibraryRootsComponentDescriptor();
  }
}
