/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.execution.ui;

import com.intellij.execution.CommonProgramRunConfigurationParameters;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.util.ProgramParametersConfigurator;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.MacroAwareTextBrowseFolderListener;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.TextAccessor;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class CommonProgramParametersPanel extends JPanel implements PanelWithAnchor {
  private LabeledComponent<RawCommandLineEditor> myProgramParametersComponent;
  private LabeledComponent<JComponent> myWorkingDirectoryComponent;
  protected TextFieldWithBrowseButton myWorkingDirectoryField;
  private EnvironmentVariablesComponent myEnvVariablesComponent;
  protected JComponent myAnchor;

  private Module myModuleContext = null;
  private boolean myHasModuleMacro = false;

  public CommonProgramParametersPanel() {
    this(true);
  }

  public CommonProgramParametersPanel(boolean init) {
    super();

    setLayout(new VerticalFlowLayout(VerticalFlowLayout.MIDDLE, 0, 5, true, false));

    if (init) {
      init();
    }
  }

  protected void init() {
    initComponents();
    updateUI();
    setupAnchor();
  }

  protected void setupAnchor() {
    myAnchor = UIUtil.mergeComponentsWithAnchor(myProgramParametersComponent, myWorkingDirectoryComponent, myEnvVariablesComponent);
  }

  @Nullable
  protected Project getProject() {
    return myModuleContext != null ? myModuleContext.getProject() : null;
  }

  protected void initComponents() {
    myProgramParametersComponent = LabeledComponent.create(new RawCommandLineEditor(),
                                                           ExecutionBundle.message("run.configuration.program.parameters"));

    FileChooserDescriptor fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    //noinspection DialogTitleCapitalization
    fileChooserDescriptor.setTitle(ExecutionBundle.message("select.working.directory.message"));
    myWorkingDirectoryField = new TextFieldWithBrowseButton();
    myWorkingDirectoryField.addBrowseFolderListener(new MacroAwareTextBrowseFolderListener(fileChooserDescriptor, getProject()) {
      @Override
      public void actionPerformed(ActionEvent e) {
        myFileChooserDescriptor.putUserData(LangDataKeys.MODULE_CONTEXT, myModuleContext);
        setProject(getProject());
        super.actionPerformed(e);
      }
    });

    myWorkingDirectoryComponent = LabeledComponent.create(createComponentWithMacroBrowse(myWorkingDirectoryField), ExecutionBundle.message("run.configuration.working.directory.label"));
    myEnvVariablesComponent = new EnvironmentVariablesComponent();

    myEnvVariablesComponent.setLabelLocation(BorderLayout.WEST);
    myProgramParametersComponent.setLabelLocation(BorderLayout.WEST);
    myWorkingDirectoryComponent.setLabelLocation(BorderLayout.WEST);

    addComponents();

    setPreferredSize(new Dimension(10, 10));

    copyDialogCaption(myProgramParametersComponent);
  }

  protected JComponent createComponentWithMacroBrowse(@NotNull final TextFieldWithBrowseButton textAccessor) {
    final FixedSizeButton button = new FixedSizeButton(textAccessor);
    button.setIcon(AllIcons.RunConfigurations.Variables);
    button.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        List<String> macros = new SmartList<>(ContainerUtil.map(PathMacros.getInstance().getUserMacroNames(), s -> "$" + s + "$"));
        if (myModuleContext != null || myHasModuleMacro) {
          macros.add("$" + PathMacroUtil.MODULE_DIR_MACRO_NAME + "$");
          macros.add(ProgramParametersConfigurator.MODULE_WORKING_DIR);
        }

        final JList list = new JBList(ArrayUtil.toStringArray(macros));
        JBPopupFactory.getInstance().createListPopupBuilder(list).setItemChoosenCallback(() -> {
          final Object value = list.getSelectedValue();
          if (value instanceof String) {
            textAccessor.setText((String)value);
          }
        }).setMovable(false).setResizable(false).createPopup().showUnderneathOf(button);
      }
    });

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(textAccessor, BorderLayout.CENTER);
    panel.add(button, BorderLayout.EAST);
    return panel;
  }

  protected void addComponents() {
    add(myProgramParametersComponent);
    add(myWorkingDirectoryComponent);
    add(myEnvVariablesComponent);
  }

  protected void copyDialogCaption(final LabeledComponent<RawCommandLineEditor> component) {
    final RawCommandLineEditor rawCommandLineEditor = component.getComponent();
    rawCommandLineEditor.setDialogCaption(component.getRawText());
    component.getLabel().setLabelFor(rawCommandLineEditor.getTextField());
  }

  public void setProgramParametersLabel(String textWithMnemonic) {
    myProgramParametersComponent.setText(textWithMnemonic);
    copyDialogCaption(myProgramParametersComponent);
  }

  public void setProgramParameters(String params) {
    myProgramParametersComponent.getComponent().setText(params);
  }

  public void setWorkingDirectory(String dir) {
    myWorkingDirectoryField.setText(dir);
  }

  public void setModuleContext(Module moduleContext) {
    myModuleContext = moduleContext;
  }

  public void setHasModuleMacro() {
    myHasModuleMacro = true;
  }

  public LabeledComponent<RawCommandLineEditor> getProgramParametersComponent() {
    return myProgramParametersComponent;
  }

  @Override
  public JComponent getAnchor() {
    return myAnchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    myAnchor = anchor;
    myProgramParametersComponent.setAnchor(anchor);
    myWorkingDirectoryComponent.setAnchor(anchor);
    myEnvVariablesComponent.setAnchor(anchor);
  }

  public void applyTo(CommonProgramRunConfigurationParameters configuration) {
    configuration.setProgramParameters(fromTextField(myProgramParametersComponent.getComponent(), configuration));
    configuration.setWorkingDirectory(fromTextField(myWorkingDirectoryField, configuration));

    configuration.setEnvs(myEnvVariablesComponent.getEnvs());
    configuration.setPassParentEnvs(myEnvVariablesComponent.isPassParentEnvs());
  }

  @Nullable
  protected String fromTextField(@NotNull TextAccessor textAccessor, @NotNull CommonProgramRunConfigurationParameters configuration) {
    return textAccessor.getText();
  }

  public void reset(CommonProgramRunConfigurationParameters configuration) {
    setProgramParameters(configuration.getProgramParameters());
    setWorkingDirectory(PathUtil.toSystemDependentName(configuration.getWorkingDirectory()));

    myEnvVariablesComponent.setEnvs(configuration.getEnvs());
    myEnvVariablesComponent.setPassParentEnvs(configuration.isPassParentEnvs());
  }
}
