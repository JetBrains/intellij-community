// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Implement this extension point to provide a custom icon and description for specific libraries in Project Structure dialog. Belonging
 * to a specific kind is determined automatically by classes roots of a library using {@link #detect(List)} method, if you need to specify
 * it explicitly use {@link LibraryType} extension point instead. <br>
 * The implementation should be registered in plugin.xml:
 * <pre>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;
 * &nbsp;&nbsp;&lt;library.presentationProvider implementation="qualified-class-name"/&gt;
 * &lt;/extensions&gt;
 * </pre>
 *
 * @see LibraryType
 */
public abstract class LibraryPresentationProvider<P extends LibraryProperties> {
  public static final ExtensionPointName<LibraryPresentationProvider> EP_NAME = ExtensionPointName.create("com.intellij.library.presentationProvider");
  private final LibraryKind myKind;

  protected LibraryPresentationProvider(@NotNull LibraryKind kind) {
    myKind = kind;
  }

  public @NotNull LibraryKind getKind() {
    return myKind;
  }

  /**
   * @deprecated override {@link #getIcon(LibraryProperties)} instead
   */
  @Deprecated
  public @Nullable Icon getIcon() {
    return null;
  }

  public @Nullable Icon getIcon(@Nullable P properties) {
    return getIcon();
  }

  /**
   * @return description of a library to be shown in 'Library Editor' in 'Project Structure' dialog
   */
  public @Nullable @Nls String getDescription(@NotNull P properties) {
    return null;
  }

  /**
   * Returns non-null value if a library with classes roots {@code classesRoots} is of a kind described by this provider.
   */
  public abstract @Nullable P detect(@NotNull List<VirtualFile> classesRoots);
}
