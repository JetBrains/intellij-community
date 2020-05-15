// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.CompositeShortcutSet;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemIndependent;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.Collection;

class RunConfigurationStorageUi {
  private final JPanel myMainPanel;
  private final ComboBox<String> myPathComboBox;

  @NonNls @SystemIndependent private final String myDotIdeaStoragePath;

  private Runnable myClosePopupAction;

  RunConfigurationStorageUi(@NotNull Project project,
                            @NotNull String dotIdeaStoragePath,
                            @NotNull Function<String, String> pathToErrorMessage,
                            @NotNull Disposable uiDisposable) {
    myDotIdeaStoragePath = dotIdeaStoragePath;
    myPathComboBox = createPathComboBox(project, uiDisposable);

    ComponentValidator validator = new ComponentValidator(uiDisposable);
    JTextComponent comboBoxEditorComponent = (JTextComponent)myPathComboBox.getEditor().getEditorComponent();
    validator.withValidator(() -> {
      String errorMessage = pathToErrorMessage.fun(getPath());
      return errorMessage != null ? new ValidationInfo(errorMessage, myPathComboBox) : null;
    })
      .andRegisterOnDocumentListener(comboBoxEditorComponent)
      .installOn(comboBoxEditorComponent);

    JPanel comboBoxPanel = UI.PanelFactory.panel(myPathComboBox)
      .withLabel(ExecutionBundle.message("run.configuration.store.in")).moveLabelOnTop()
      .withComment(getCompatibilityHintText(project), false)
      .withCommentHyperlinkListener(e -> {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          myPathComboBox.getEditor().setItem(FileUtil.toSystemDependentName(myDotIdeaStoragePath));
        }
      })
      .createPanel();

    JButton doneButton = new JButton(ExecutionBundle.message("run.configuration.done.button"));
    doneButton.addActionListener(e -> myClosePopupAction.run());
    JPanel doneButtonPanel = new JPanel(new BorderLayout());
    doneButtonPanel.add(doneButton, BorderLayout.EAST);

    myMainPanel = FormBuilder.createFormBuilder()
      .addComponent(comboBoxPanel)
      .addComponent(doneButtonPanel)
      .getPanel();

    myMainPanel.setFocusCycleRoot(true);
    myMainPanel.setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());

    // need to handle Enter keypress, otherwise Enter closes the main Run Configurations dialog.
    // Escape should also be handled manually because setHideOnKeyOutside(false) is set for this balloon.
    DumbAwareAction.create(e -> {
      if (myPathComboBox.isPopupVisible()) {
        myPathComboBox.setPopupVisible(false);
      }
      else {
        validator.updateInfo(null);
        myClosePopupAction.run();
      }
    }).registerCustomShortcutSet(new CompositeShortcutSet(CommonShortcuts.ENTER, CommonShortcuts.ESCAPE), myMainPanel, uiDisposable);
  }

  @NotNull
  private ComboBox<String> createPathComboBox(@NotNull Project project, @NotNull Disposable uiDisposable) {
    ComboBox<String> comboBox = new ComboBox<>(JBUI.scale(500));
    comboBox.setEditable(true);

    // choosefiles is set to true to be able to select project.ipr file in IPR-based projects. Other files are not visible/selectable in the chooser
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, false, false, false, false) {
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        // dotIdeaStoragePath may be a path to the project.ipr file in IPR-based projects
        if (file.getPath().equals(myDotIdeaStoragePath)) return true;
        return file.isDirectory() && super.isFileVisible(file, showHiddenFiles);
      }

      @Override
      public boolean isFileSelectable(VirtualFile file) {
        if (file.getPath().equals(myDotIdeaStoragePath)) return true;
        return file.isDirectory() &&
               super.isFileSelectable(file) &&
               ProjectFileIndex.getInstance(project).isInContent(file) &&
               !file.getPath().endsWith("/.idea") &&
               !file.getPath().contains("/.idea/");
      }
    };

    Runnable selectFolderAction = new BrowseFolderRunnable<ComboBox<String>>(null, null, project, descriptor, comboBox,
                                                                             TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT) {
    };

    comboBox.initBrowsableEditor(selectFolderAction, uiDisposable);
    return comboBox;
  }

  @NotNull
  private static String getCompatibilityHintText(@NotNull Project project) {
    String oldStorage = ProjectKt.isDirectoryBased(project)
                        ? FileUtil.toSystemDependentName(".idea/runConfigurations")
                        : PathUtil.getFileName(StringUtil.notNullize(project.getProjectFilePath()));
    return ExecutionBundle.message("run.configuration.storage.compatibility.hint", oldStorage);
  }

  JPanel getMainPanel() {
    return myMainPanel;
  }

  void reset(@NotNull @SystemIndependent String folderPath, Collection<String> pathsToSuggest, @NotNull Runnable closePopupAction) {
    myPathComboBox.setSelectedItem(FileUtil.toSystemDependentName(folderPath));

    for (String s : pathsToSuggest) {
      myPathComboBox.addItem(FileUtil.toSystemDependentName(s));
    }

    myClosePopupAction = closePopupAction;
  }

  @NotNull String getPath() {
    return StringUtil.trimTrailing(FileUtil.toSystemIndependentName(myPathComboBox.getEditor().getItem().toString().trim()), '/');
  }
}
