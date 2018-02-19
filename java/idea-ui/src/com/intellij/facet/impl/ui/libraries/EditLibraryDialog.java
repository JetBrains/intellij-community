/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.facet.impl.ui.libraries;

import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryNameAndLevelPanel;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryRootsComponent;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Dmitry Avdeev
 */
public class EditLibraryDialog extends DialogWrapper {

  private JPanel myEditorPanel;
  private JPanel myPanel;
  private JPanel myNameAndLevelPanelWrapper;
  private final LibraryNameAndLevelPanel myNameAndLevelPanel;
  private final LibraryCompositionSettings mySettings;
  private final LibraryEditor myLibraryEditor;
  private final LibraryRootsComponent myLibraryRootsComponent;
  private final FormBuilder myBuilder;

  public EditLibraryDialog(Component parent, LibraryCompositionSettings settings, final LibraryEditor libraryEditor) {
    super(parent, true);
    mySettings = settings;
    myLibraryEditor = libraryEditor;
    myLibraryRootsComponent = new LibraryRootsComponent(null, libraryEditor);
    myLibraryRootsComponent.resetProperties();

    Disposer.register(getDisposable(), myLibraryRootsComponent);

    final boolean newLibrary = libraryEditor instanceof NewLibraryEditor;
    setTitle((newLibrary ? "Create" : "Edit") + " Library");

    myBuilder = LibraryNameAndLevelPanel.createFormBuilder();
    myNameAndLevelPanel = new LibraryNameAndLevelPanel(myBuilder, libraryEditor.getName(), newLibrary ? settings.getNewLibraryLevel() : null);
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    JComponent editor = myLibraryRootsComponent.getComponent();
    myEditorPanel.add(editor);
    myNameAndLevelPanelWrapper.add(myBuilder.getPanel());
    return myPanel;
  }

  @Override
  protected void doOKAction() {
    myLibraryEditor.setName(myNameAndLevelPanel.getLibraryName());
    myLibraryRootsComponent.applyProperties();
    if (myLibraryEditor instanceof NewLibraryEditor) {
      mySettings.setNewLibraryLevel(myNameAndLevelPanel.getLibraryLevel());
    }
    super.doOKAction();
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return "Edit_Library_dialog";
  }
}
