// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent;
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public abstract class LibraryPropertiesEditorBase<P extends LibraryProperties, T extends LibraryType<P>> extends LibraryPropertiesEditor {
  protected final Logger logger = Logger.getInstance(getClass());

  private boolean myModified;
  private JPanel myMainPanel;
  private JLabel myDescriptionLabel;
  private JButton myEditButton;
  protected JButton myReloadButton;
  protected final LibraryEditorComponent<P> myEditorComponent;
  protected final T myLibraryType;

  protected LibraryPropertiesEditorBase(final LibraryEditorComponent<P> editorComponent,
                                        T libraryType, @Nullable @NlsContexts.Button String editButtonText) {
    myEditorComponent = editorComponent;
    myLibraryType = libraryType;
    updateDescription();
    if (editButtonText != null) {
      myEditButton.setText(UIUtil.replaceMnemonicAmpersand(editButtonText));
    }
    myEditButton.setVisible(!myEditorComponent.isNewLibrary());
    myEditButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        edit();
      }
    });
    myMainPanel.setBorder(JBUI.Borders.empty(0, 10, 5, 10));
  }

  protected JPanel getMainPanel() {
    return myMainPanel;
  }

  protected void updateDescription() {
    String description = myLibraryType.getDescription(myEditorComponent.getProperties());
    myDescriptionLabel.setText(description);
    myDescriptionLabel.setToolTipText(description);
  }

  protected abstract void edit();

  protected void setModified() {
    myModified = true;
    updateDescription();
  }

  @Override
  public @NotNull JComponent createComponent() {
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    return myModified;
  }

  @Override
  public void reset() {
    updateDescription();
  }
}
