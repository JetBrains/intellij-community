/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.find.impl;

import com.intellij.find.FindBundle;
import com.intellij.find.FindInProjectSettings;
import com.intellij.find.FindModel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

class FindPopupDirectoryChooser extends JPanel {
  @NotNull private final FindUIHelper myHelper;
  @NotNull private final Project myProject;
  @NotNull private final FindPopupPanel myFindPopupPanel;
  @NotNull private final ComboBox<String> myDirectoryComboBox;

  FindPopupDirectoryChooser(@NotNull FindPopupPanel panel) {
    super(new BorderLayout());

    myHelper = panel.getHelper();
    myProject = panel.getProject();
    myFindPopupPanel = panel;
    myDirectoryComboBox = new ComboBox<>(200);

    Component editorComponent = myDirectoryComboBox.getEditor().getEditorComponent();
    if (editorComponent instanceof JTextField) {
      JTextField field = (JTextField)editorComponent;
      field.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          myFindPopupPanel.scheduleResultsUpdate();
        }
      });
      field.setColumns(40);
    }
    myDirectoryComboBox.setEditable(true);
    myDirectoryComboBox.setMaximumRowCount(8);

    ActionListener restartSearchListener = e -> myFindPopupPanel.scheduleResultsUpdate();
    myDirectoryComboBox.addActionListener(restartSearchListener);

    FixedSizeButton mySelectDirectoryButton = new FixedSizeButton(myDirectoryComboBox);
    TextFieldWithBrowseButton.MyDoClickAction.addTo(mySelectDirectoryButton, myDirectoryComboBox);
    mySelectDirectoryButton.setMargin(JBUI.emptyInsets());

    mySelectDirectoryButton.addActionListener(__ -> {
      FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
      descriptor.setForcedToUseIdeaFileChooser(true);
      myFindPopupPanel.getCanClose().set(false);
      FileChooser.chooseFiles(descriptor, myProject, myFindPopupPanel, null,
                              new FileChooser.FileChooserConsumer() {
        @Override
        public void consume(List<VirtualFile> files) {
          ApplicationManager.getApplication().invokeLater(() -> {
            myFindPopupPanel.getCanClose().set(true);
            IdeFocusManager.getInstance(myProject).requestFocus(myDirectoryComboBox.getEditor().getEditorComponent(), true);
            myHelper.getModel().setDirectoryName(files.get(0).getPresentableUrl());
            myDirectoryComboBox.getEditor().setItem(files.get(0).getPresentableUrl());
          });
        }

        @Override
        public void cancelled() {
          ApplicationManager.getApplication().invokeLater(() -> {
            myFindPopupPanel.getCanClose().set(true);
            IdeFocusManager.getInstance(myProject).requestFocus(myDirectoryComboBox.getEditor().getEditorComponent(), true);
          });
        }
      });
    });

    MyRecursiveDirectoryAction recursiveDirectoryAction = new MyRecursiveDirectoryAction();
    int mnemonicModifiers = SystemInfo.isMac ? InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK : InputEvent.ALT_DOWN_MASK;
    recursiveDirectoryAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_Y, mnemonicModifiers)), myFindPopupPanel);

    add(myDirectoryComboBox, BorderLayout.CENTER);
    JPanel buttonsPanel = new JPanel(new GridLayout(1, 2));
    buttonsPanel.add(mySelectDirectoryButton);
    buttonsPanel.add(FindPopupPanel.createToolbar(recursiveDirectoryAction).getComponent()); //check if toolbar updates the button with no delays
    add(buttonsPanel, BorderLayout.EAST);
  }

  void initByModel(@NotNull FindModel findModel) {
    final String directoryName = findModel.getDirectoryName();
    java.util.List<String> strings = FindInProjectSettings.getInstance(myProject).getRecentDirectories();

    if (myDirectoryComboBox.getItemCount() > 0) {
      myDirectoryComboBox.removeAllItems();
    }
    if (directoryName != null && !directoryName.isEmpty()) {
      if (strings.contains(directoryName)) {
        strings.remove(directoryName);
      }
      myDirectoryComboBox.addItem(directoryName);
    }
    for (int i = strings.size() - 1; i >= 0; i--) {
      myDirectoryComboBox.addItem(strings.get(i));
    }
    if (myDirectoryComboBox.getItemCount() == 0) {
      myDirectoryComboBox.addItem("");
    }
  }

  @NotNull
  public ComboBox getComboBox() {
    return myDirectoryComboBox;
  }

  @NotNull
  public String getDirectory() {
    return (String)myDirectoryComboBox.getEditor().getItem();
  }

  @Nullable
  public ValidationInfo validate(@NotNull FindModel model) {
    VirtualFile directory = FindInProjectUtil.getDirectory(model);
    if (directory == null) {
      return new ValidationInfo(FindBundle.message("find.directory.not.found.error", getDirectory()), myDirectoryComboBox);
    }
    return null;
  }

  private class MyRecursiveDirectoryAction extends ToggleAction {
    MyRecursiveDirectoryAction() {
      super(FindBundle.message("find.scope.directory.recursive.checkbox"), "Recursively", AllIcons.General.Recursive);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myHelper.getModel().isWithSubdirectories();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myHelper.getModel().setWithSubdirectories(state);
      myFindPopupPanel.scheduleResultsUpdate();
    }
  }
}
