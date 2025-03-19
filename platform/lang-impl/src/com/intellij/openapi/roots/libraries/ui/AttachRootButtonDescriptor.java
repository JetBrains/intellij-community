// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.libraries.ui;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Describes an 'attach' button in the library roots component.
 *
 * @see ChooserBasedAttachRootButtonDescriptor
 */
public abstract class AttachRootButtonDescriptor {
  private final OrderRootType myOrderRootType;
  protected final @NlsContexts.Button String myButtonText;
  private final Icon myToolbarIcon;

  /**
   * Creates a descriptor for 'attach' button shown in popup when user click on '+' button.
   * Consider using {@link #AttachRootButtonDescriptor(OrderRootType, Icon, String)} instead.
   */
  protected AttachRootButtonDescriptor(@NotNull OrderRootType orderRootType, @NotNull @NlsContexts.Button String buttonText) {
    myOrderRootType = orderRootType;
    myButtonText = buttonText;
    myToolbarIcon = null;
  }

  /**
   * Creates a descriptor for 'attach' button shown in toolbar of a library editor
   */
  protected AttachRootButtonDescriptor(@NotNull OrderRootType orderRootType,
                                       @NotNull Icon toolbarIcon,
                                       @NotNull @NlsContexts.Button String buttonText) {
    myOrderRootType = orderRootType;
    myButtonText = buttonText;
    myToolbarIcon = toolbarIcon;
  }

  public abstract VirtualFile[] selectFiles(@NotNull JComponent parent, @Nullable VirtualFile initialSelection,
                                            @Nullable Module contextModule, @NotNull LibraryEditor libraryEditor);

  public @NlsContexts.Button String getButtonText() {
    return myButtonText;
  }

  public OrderRootType getRootType() {
    return myOrderRootType;
  }

  public boolean addAsJarDirectories() {
    return false;
  }

  public VirtualFile @NotNull [] scanForActualRoots(VirtualFile @NotNull [] rootCandidates, JComponent parent) {
    return rootCandidates;
  }

  public @Nullable Icon getToolbarIcon() {
    return myToolbarIcon;
  }
}
