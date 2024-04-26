// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;

/**
 * @author Konstantin Bulenkov
 */
public final class UIComponentFileEditor extends UserDataHolderBase implements FileEditor {
  private final @NotNull UIComponentVirtualFile myFile;
  private final @NotNull UIComponentVirtualFile.Content myUi;
  private @Nullable JComponent myComponent;

  public UIComponentFileEditor(@NotNull UIComponentVirtualFile file) {
    myFile = file;
    myUi = file.createContent(this);
  }

  @Override
  public @NotNull JComponent getComponent() {
    if (myComponent == null) {
      myComponent = myUi.createComponent();
    }
    return myComponent;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    assert myComponent != null;
    return myUi.getPreferredFocusedComponent(myComponent);
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Title) @NotNull String getName() {
    return myFile.getName();
  }

  @Override
  public void setState(@NotNull FileEditorState state) {

  }

  @Override
  public @NotNull VirtualFile getFile() {
    return myFile;
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {

  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {

  }

  @Override
  public void dispose() {

  }
}
