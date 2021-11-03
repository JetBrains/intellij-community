// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
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
public class UIComponentFileEditor extends UserDataHolderBase implements FileEditor {
  private final UIComponentVirtualFile myFile;
  private JComponent myComponent;

  public UIComponentFileEditor(@NotNull UIComponentVirtualFile file) {
    myFile = file;
  }

  @Override
  public @NotNull JComponent getComponent() {
    if (myComponent == null) {
      myComponent = myFile.getUi().createComponent();
    }
    return myComponent;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myFile.getUi().getPreferredFocusedComponent();
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
  public @Nullable FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Override
  public void dispose() {

  }
}
