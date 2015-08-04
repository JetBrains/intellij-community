/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.util.projectWizard;

import com.intellij.BundleBase;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbModePermission;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.wm.impl.welcomeScreen.AbstractActionWithPanel;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.platform.WebProjectGenerator;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.NullableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;

public class ProjectSettingsStepBase extends AbstractActionWithPanel implements DumbAware {
  protected final DirectoryProjectGenerator myProjectGenerator;
  private final NullableConsumer<ProjectSettingsStepBase> myCallback;
  protected TextFieldWithBrowseButton myLocationField;
  protected File myProjectDirectory;
  protected JButton myCreateButton;
  protected JLabel myErrorLabel;

  public ProjectSettingsStepBase(DirectoryProjectGenerator projectGenerator,
                                 NullableConsumer<ProjectSettingsStepBase> callback) {
    super();
    getTemplatePresentation().setIcon(projectGenerator.getLogo());
    getTemplatePresentation().setText(projectGenerator.getName());
    myProjectGenerator = projectGenerator;
    if (projectGenerator instanceof WebProjectTemplate) {
      ((WebProjectTemplate)projectGenerator).reset();
    }
    myCallback = callback;
    myProjectDirectory = findSequentNonExistingUntitled();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
  }

  @Override
  public JPanel createPanel() {
    final JPanel mainPanel = new JPanel(new BorderLayout());

    final JLabel label = createErrorLabel();
    final JButton button = createActionButton();
    button.addActionListener(createCloseActionListener());
    final JPanel scrollPanel = createAndFillContentPanel();
    initGeneratorListeners();
    registerValidators();
    final JBScrollPane scrollPane = new JBScrollPane(scrollPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                     ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setBorder(null);
    mainPanel.add(scrollPane, BorderLayout.CENTER);

    final JPanel bottomPanel = new JPanel(new BorderLayout());

    bottomPanel.add(label, BorderLayout.NORTH);
    bottomPanel.add(button, BorderLayout.EAST);
    mainPanel.add(bottomPanel, BorderLayout.SOUTH);
    return mainPanel;
  }

  protected final JLabel createErrorLabel() {
    JLabel errorLabel = new JLabel("");
    errorLabel.setForeground(JBColor.RED);

    myErrorLabel = errorLabel;

    return errorLabel;
  }

  protected final JButton createActionButton() {
    JButton button = new JButton("Create");
    button.putClientProperty(DialogWrapper.DEFAULT_ACTION, Boolean.TRUE);

    myCreateButton = button;
    return button;
  }

  @NotNull
  protected final ActionListener createCloseActionListener() {
    return new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        boolean isValid = checkValid();
        if (isValid && myCallback != null) {
          final DialogWrapper dialog = DialogWrapper.findInstance(myCreateButton);
          if (dialog != null) {
            dialog.close(DialogWrapper.OK_EXIT_CODE);
          }
          DumbService.allowStartingDumbModeInside(DumbModePermission.MAY_START_BACKGROUND, new Runnable() {
            @Override
            public void run() {
              myCallback.consume(ProjectSettingsStepBase.this);
            }
          });
        }
      }
    };
  }

  protected final JPanel createContentPanelWithAdvancedSettingsPanel() {
    final JPanel basePanel = createBasePanel();
    final JPanel scrollPanel = new JPanel(new BorderLayout());
    scrollPanel.add(basePanel, BorderLayout.NORTH);
    final JPanel advancedSettings = createAdvancedSettings();
    if (advancedSettings != null) {
      scrollPanel.add(advancedSettings, BorderLayout.CENTER);
    }
    return scrollPanel;
  }

  protected void initGeneratorListeners() {
    if (myProjectGenerator instanceof WebProjectTemplate) {
      ((WebProjectTemplate)myProjectGenerator).getPeer().addSettingsStateListener(new WebProjectGenerator.SettingsStateListener() {
        @Override
        public void stateChanged(boolean validSettings) {
          checkValid();
        }
      });
    }
  }

  protected final Icon getIcon() {
    return myProjectGenerator.getLogo();
  }

  protected JPanel createBasePanel() {
    final JPanel panel = new JPanel(new VerticalFlowLayout(0, 2));
    final LabeledComponent<TextFieldWithBrowseButton> component = createLocationComponent();
    component.setLabelLocation(BorderLayout.WEST);
    panel.add(component);

    return panel;
  }

  protected void registerValidators() {
    myLocationField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        checkValid();
      }
    });
    final ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        checkValid();
      }
    };
    myLocationField.getTextField().addActionListener(listener);
  }

  public boolean checkValid() {
    if (myLocationField == null) return true;
    final String projectName = myLocationField.getText();
    setErrorText(null);

    if (projectName.trim().isEmpty()) {
      setErrorText("Project name can't be empty");
      return false;
    }
    if (myLocationField.getText().indexOf('$') >= 0) {
      setErrorText("Project directory name must not contain the $ character");
      return false;
    }
    if (myProjectGenerator != null) {
      final String baseDirPath = myLocationField.getTextField().getText();
      ValidationResult validationResult = myProjectGenerator.validate(baseDirPath);
      if (!validationResult.isOk()) {
        setErrorText(validationResult.getErrorMessage());
        return false;
      }
      if (myProjectGenerator instanceof WebProjectTemplate) {
        final WebProjectGenerator.GeneratorPeer peer = ((WebProjectTemplate)myProjectGenerator).getPeer();
        final ValidationInfo validationInfo = peer.validate();
        if (validationInfo != null && !peer.isBackgroundJobRunning()) {
          setErrorText(validationInfo.message);
          return false;
        }
      }
    }

    return true;
  }

  protected JPanel createAndFillContentPanel() {
    if (!(myProjectGenerator instanceof WebProjectTemplate)) return createContentPanelWithAdvancedSettingsPanel();

    WebProjectSettingsStepWrapper settingsStep = new WebProjectSettingsStepWrapper();
    ((WebProjectTemplate)myProjectGenerator).getPeer().buildUI(settingsStep);

    //back compatibility: some plugins can implement only GeneratorPeer#getComponent() method
    if (settingsStep.isEmpty()) return createContentPanelWithAdvancedSettingsPanel();

    final JPanel jPanel = new JPanel(new VerticalFlowLayout(0, 5));
    List<LabeledComponent> labeledComponentList = ContainerUtil.newArrayList();
    labeledComponentList.add(createLocationComponent());
    labeledComponentList.addAll(settingsStep.getFields());

    final JPanel scrollPanel = new JPanel(new BorderLayout());
    scrollPanel.add(jPanel, BorderLayout.NORTH);

    for (LabeledComponent component : labeledComponentList) {
      component.setLabelLocation(BorderLayout.WEST);
      jPanel.add(component);
    }

    for (JComponent component : settingsStep.getComponents()) {
      jPanel.add(component);
    }

    UIUtil.mergeComponentsWithAnchor(labeledComponentList);

    return scrollPanel;
  }

  public void setErrorText(@Nullable String text) {
    myErrorLabel.setText(text);
    myErrorLabel.setForeground(MessageType.ERROR.getTitleForeground());
    myErrorLabel.setIcon(text == null ? null : AllIcons.Actions.Lightning);
    myCreateButton.setEnabled(text == null);
  }

  public void setWarningText(@Nullable String text) {
    myErrorLabel.setText("Note: " + text + "  ");
    myErrorLabel.setForeground(MessageType.WARNING.getTitleForeground());
  }

  @Nullable
  protected JPanel createAdvancedSettings() {
    if (myProjectGenerator instanceof WebProjectTemplate) {
      final JPanel jPanel = new JPanel(new VerticalFlowLayout(0, 5));
      jPanel.add(((WebProjectTemplate)myProjectGenerator).getPeer().getComponent());
      return jPanel;
    }
    return null;
  }

  public DirectoryProjectGenerator getProjectGenerator() {
    return myProjectGenerator;
  }

  public final String getProjectLocation() {
    return myLocationField.getText();
  }

  public final void setLocation(@NotNull final String location) {
    myLocationField.setText(location);
  }

  protected final LabeledComponent<TextFieldWithBrowseButton> createLocationComponent() {
    myLocationField = new TextFieldWithBrowseButton();
    myProjectDirectory = findSequentNonExistingUntitled();
    myLocationField.setText(myProjectDirectory.toString());

    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myLocationField.addBrowseFolderListener("Select base directory", "Select base directory for the Project",
                                            null, descriptor);
    return LabeledComponent.create(myLocationField, BundleBase.replaceMnemonicAmpersand("&Location"));
  }

  private static File findSequentNonExistingUntitled() {
    return FileUtil.findSequentNonexistentFile(new File(ProjectUtil.getBaseDir()), "untitled", "");
  }
}
