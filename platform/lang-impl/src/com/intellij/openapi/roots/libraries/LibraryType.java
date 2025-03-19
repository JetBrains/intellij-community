// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.UnknownLibraryKind;
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent;
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor;
import com.intellij.openapi.roots.libraries.ui.LibraryRootsComponentDescriptor;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.util.NlsContexts.Label;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Override this class to provide custom library type. Type and properties for custom libraries are stored in project configuration files. If
 * they can be detected automatically it's better to use {@link LibraryPresentationProvider} extension point instead. <br>
 * The implementation should be registered in plugin.xml:
 * <p>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;library.type implementation="qualified-class-name"/&gt;<br>
 * &lt;/extensions&gt;
 *
 * @see LibraryPresentationProvider
 */
public abstract class LibraryType<P extends LibraryProperties> extends LibraryPresentationProvider<P> {
  public static final ExtensionPointName<LibraryType<?>> EP_NAME = ExtensionPointName.create("com.intellij.library.type");
  
  public static final OrderRootType[] DEFAULT_EXTERNAL_ROOT_TYPES = {OrderRootType.CLASSES};

  protected LibraryType(@NotNull PersistentLibraryKind<P> libraryKind) {
    super(libraryKind);
  }

  @Override
  public @NotNull PersistentLibraryKind<P> getKind() {
    return (PersistentLibraryKind<P>) super.getKind();
  }

  /**
   * @return text to show in 'New Library' popup. Return {@code null} if the type should not be shown in the 'New Library' popup
   */
  public abstract @Label @Nullable String getCreateActionName();

  /**
   * Called when a new library of this type is created in Project Structure dialog
   */
  public abstract @Nullable NewLibraryConfiguration createNewLibrary(@NotNull JComponent parentComponent, @Nullable VirtualFile contextDirectory,
                                                           @NotNull Project project);

  /**
   * @return {@code true} if library of this type can be added as a dependency to {@code module}
   */
  public boolean isSuitableModule(@NotNull Module module, @NotNull FacetsProvider facetsProvider) {
    return true;
  }

  /**
   * Override this method to customize the library roots editor
   * @return {@link LibraryRootsComponentDescriptor} instance
   */
  public @Nullable LibraryRootsComponentDescriptor createLibraryRootsComponentDescriptor() {
    return null;
  }

  public abstract @Nullable LibraryPropertiesEditor createPropertiesEditor(@NotNull LibraryEditorComponent<P> editorComponent);

  @Override
  public P detect(@NotNull List<VirtualFile> classesRoots) {
    return null;
  }

  /**
   * @return Root types to collect library files which do not belong to the project and therefore
   *         indicate that the library is external.
   */
  public OrderRootType @NotNull [] getExternalRootTypes() {
    return DEFAULT_EXTERNAL_ROOT_TYPES;
  }

  public static @NotNull LibraryType findByKind(@NotNull LibraryKind kind) {
    for (LibraryType<?> type : EP_NAME.getExtensions()) {
      if (type.getKind() == kind) {
        return type;
      }
    }
    if (kind instanceof UnknownLibraryKind) {
      return new UnknownLibraryType((UnknownLibraryKind)kind);
    }
    throw new IllegalArgumentException("Library with kind " + kind + " is not registered");
  }
}
