// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.libraries.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.ui.impl.LibraryRootsDetectorImpl;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Allows to customize a library editor
 *
 * @see com.intellij.openapi.roots.libraries.LibraryType#createLibraryRootsComponentDescriptor
 */
public abstract class LibraryRootsComponentDescriptor {
  /**
   * Defines presentation for root type nodes in the library roots editor
   * @return custom presentation or {@code null} if default presentation should be used
   */
  public abstract @Nullable OrderRootTypePresentation getRootTypePresentation(@NotNull OrderRootType type);

  /**
   * Provides separate detectors for root types supported by the library type.
   *
   * @return non-empty list of {@link RootDetector}'s implementations
   */
  public abstract @NotNull List<? extends RootDetector> getRootDetectors();

  /**
   * Provides root detector for 'Attach Files' button. It will be used to automatically assign {@link OrderRootType}s for selected files.
   * Also this detector is used when a new library is created
   *
   * @return {@link LibraryRootsDetector}'s implementation
   */
  public @NotNull LibraryRootsDetector getRootsDetector() {
    final List<? extends RootDetector> detectors = getRootDetectors();
    if (detectors.isEmpty()) {
      throw new IllegalStateException("Detectors list is empty for " + this);
    }
    return new LibraryRootsDetectorImpl(detectors);
  }


  /**
   * @return descriptor for the file chooser which will be shown when 'Attach Files' button is pressed
   */
  public @NotNull FileChooserDescriptor createAttachFilesChooserDescriptor(@Nullable String libraryName) {
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createMultipleJavaPathDescriptor();
    descriptor.setTitle(StringUtil.isEmpty(libraryName) ? ProjectBundle.message("library.attach.files.action")
                                                        : ProjectBundle.message("library.attach.files.to.library.action", libraryName));
    descriptor.setDescription(ProjectBundle.message("library.attach.files.description"));
    return descriptor;
  }

  /**
   * Creates a instance which will be notified when a root is removed in the library editor.
   */
  public @NotNull RootRemovalHandler createRootRemovalHandler() {
    return new RootRemovalHandler() {
      @Override
      public void onRootRemoved(@NotNull String rootUrl, @NotNull OrderRootType rootType, @NotNull LibraryEditor libraryEditor) {
      }
    };
  }


  /**
   * @return descriptors for additional 'Attach' buttons in the library roots editor
   */
  public abstract @NotNull List<? extends AttachRootButtonDescriptor> createAttachButtons();

  /**
   * @return Array of root types supported by a library type associated with the roots
   *         component descriptor. All persistent root types are returned by default. 
   */
  public OrderRootType[] getRootTypes() {
    return OrderRootType.getAllTypes();
  }

  public @NlsActions.ActionText String getAttachFilesActionName() {
    return ProjectBundle.message("button.text.attach.files");
  }

  public interface RootRemovalHandler {
    void onRootRemoved(@NotNull String rootUrl, @NotNull OrderRootType rootType, @NotNull LibraryEditor libraryEditor);
  }
}
