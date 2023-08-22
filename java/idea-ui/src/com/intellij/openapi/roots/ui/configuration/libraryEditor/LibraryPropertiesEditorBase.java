/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @NotNull
  @Override
  public JComponent createComponent() {
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
