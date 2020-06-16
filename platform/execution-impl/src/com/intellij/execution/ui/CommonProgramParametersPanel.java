// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.CommonProgramRunConfigurationParameters;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.util.ProgramParametersConfigurator;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.macro.EditorMacro;
import com.intellij.ide.macro.Macro;
import com.intellij.ide.macro.MacrosDialog;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.TextAccessor;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class CommonProgramParametersPanel extends JPanel implements PanelWithAnchor {
  protected LabeledComponent<RawCommandLineEditor> myProgramParametersComponent;
  protected LabeledComponent<JComponent> myWorkingDirectoryComponent;
  protected TextFieldWithBrowseButton myWorkingDirectoryField;
  protected EnvironmentVariablesComponent myEnvVariablesComponent;
  protected JComponent myAnchor;

  private Module myModuleContext = null;
  private boolean myHasModuleMacro;
  protected final Map<String, String> myMacrosMap = new HashMap<>();

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

    // for backward compatibility: com.microsoft.tooling.msservices.intellij.azure:3.0.11
    myWorkingDirectoryField = new TextFieldWithBrowseButton();

    //noinspection DialogTitleCapitalization
    myWorkingDirectoryField.addBrowseFolderListener(ExecutionBundle.message("select.working.directory.message"), null,
                                                    getProject(),
                                                    FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                                    TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    myWorkingDirectoryComponent = LabeledComponent.create(myWorkingDirectoryField,
                                                          ExecutionBundle.message("run.configuration.working.directory.label"));

    myEnvVariablesComponent = new EnvironmentVariablesComponent();

    myEnvVariablesComponent.setLabelLocation(BorderLayout.WEST);
    myProgramParametersComponent.setLabelLocation(BorderLayout.WEST);
    myWorkingDirectoryComponent.setLabelLocation(BorderLayout.WEST);

    addComponents();
    if (isMacroSupportEnabled()) {
      initMacroSupport();
    }

    setPreferredSize(new Dimension(10, 10));

    copyDialogCaption(myProgramParametersComponent);
  }

  /**
   * @deprecated use {@link MacroComboBoxWithBrowseButton}
   */
  @Deprecated
  protected JComponent createComponentWithMacroBrowse(@NotNull final TextFieldWithBrowseButton textAccessor) {
    final FixedSizeButton button = new FixedSizeButton(textAccessor);
    button.setIcon(AllIcons.Actions.ListFiles);
    button.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final List<String> macros = ContainerUtil.map(myMacrosMap.keySet(), s -> s.startsWith("%") ? s : "$" + s + "$");
        JBPopupFactory.getInstance()
          .createPopupChooserBuilder(macros)
          .setItemChosenCallback((value) -> textAccessor.setText(value))
          .setMovable(false)
          .setResizable(false)
          .createPopup()
          .showUnderneathOf(button);
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

  /**
   * Macro support for run configuration fields is opt-in.
   * Run configurations that can handle macros (basically any using {@link ProgramParametersConfigurator} or {@link ProgramParametersUtil})
   * are encouraged to enable "add macro" inline button for program parameters and working directory fields by overriding this method,
   * and optionally overriding {@link #initMacroSupport()} to enable macros for other fields.
   */
  protected boolean isMacroSupportEnabled() {
    return false;
  }

  protected void initMacroSupport() {
    updatePathMacros();
    addMacroSupport(myProgramParametersComponent.getComponent().getEditorField(), MacrosDialog.Filters.ALL);
    addMacroSupport((ExtendableTextField)myWorkingDirectoryField.getTextField(), MacrosDialog.Filters.DIRECTORY_PATH);
  }

  public static void addMacroSupport(@NotNull ExtendableTextField textField) {
    doAddMacroSupport(textField, MacrosDialog.Filters.ALL, null);
  }

  protected void addMacroSupport(@NotNull ExtendableTextField textField,
                                 @NotNull Predicate<? super Macro> macroFilter) {
    final Predicate<? super Macro> commonMacroFilter = getCommonMacroFilter();
    doAddMacroSupport(textField, t -> commonMacroFilter.test(t) && macroFilter.test(t), myMacrosMap);
  }

  protected @NotNull Predicate<? super Macro> getCommonMacroFilter() {
    return MacrosDialog.Filters.ALL;
  }

  private static void doAddMacroSupport(@NotNull ExtendableTextField textField,
                                        @NotNull Predicate<? super Macro> macroFilter,
                                        @Nullable Map<String, String> userMacros) {
    if (Registry.is("allow.macros.for.run.configurations")) {
      MacrosDialog.addTextFieldExtension(textField, macroFilter.and(macro -> !(macro instanceof EditorMacro)), userMacros);
    }
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

  public TextAccessor getWorkingDirectoryAccessor() {
    return myWorkingDirectoryField;
  }

  public void setWorkingDirectory(String dir) {
    myWorkingDirectoryField.setText(dir);
  }

  public void setModuleContext(Module moduleContext) {
    myModuleContext = moduleContext;
    updatePathMacros();
  }

  public void setHasModuleMacro() {
    myHasModuleMacro = true;
    updatePathMacros();
  }

  protected void updatePathMacros() {
    myMacrosMap.clear();
    myMacrosMap.putAll(MacrosDialog.getPathMacros(myModuleContext != null || myHasModuleMacro));
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

  public void applyTo(@NotNull CommonProgramRunConfigurationParameters configuration) {
    configuration.setProgramParameters(fromTextField(myProgramParametersComponent.getComponent(), configuration));
    configuration.setWorkingDirectory(fromTextField(myWorkingDirectoryField, configuration));

    configuration.setEnvs(myEnvVariablesComponent.getEnvs());
    configuration.setPassParentEnvs(myEnvVariablesComponent.isPassParentEnvs());
  }

  @Nullable
  protected String fromTextField(@NotNull TextAccessor textAccessor, @NotNull CommonProgramRunConfigurationParameters configuration) {
    return textAccessor.getText();
  }

  public void reset(@NotNull CommonProgramRunConfigurationParameters configuration) {
    setProgramParameters(configuration.getProgramParameters());
    setWorkingDirectory(PathUtil.toSystemDependentName(configuration.getWorkingDirectory()));

    myEnvVariablesComponent.setEnvs(configuration.getEnvs());
    myEnvVariablesComponent.setPassParentEnvs(configuration.isPassParentEnvs());
  }
}
