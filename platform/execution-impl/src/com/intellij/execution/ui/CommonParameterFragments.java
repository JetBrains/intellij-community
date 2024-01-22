// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.CommonProgramRunConfigurationParameters;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.InputRedirectAware;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.ide.macro.MacrosDialog;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Predicates;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.TextComponentEmptyText;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.execution.util.EnvFilesUtilKt.checkEnvFiles;
import static com.intellij.openapi.util.text.StringUtil.*;

public final class CommonParameterFragments<Settings extends CommonProgramRunConfigurationParameters> {

  private final List<SettingsEditorFragment<Settings, ?>> myFragments = new ArrayList<>();
  private final SettingsEditorFragment<Settings, LabeledComponent<TextFieldWithBrowseButton>> myWorkingDirectory;
  private final Computable<Boolean> myHasModule;

  public CommonParameterFragments(@NotNull Project project, Computable<Module> moduleProvider) {
    myHasModule = () -> moduleProvider.compute() != null;
    myWorkingDirectory = createWorkingDirectory(project, moduleProvider);
    myFragments.add(myWorkingDirectory);
    myFragments.add(createEnvParameters());
  }

  public @NotNull SettingsEditorFragment<Settings, RawCommandLineEditor> programArguments() {
    RawCommandLineEditor programArguments = new RawCommandLineEditor();
    CommandLinePanel.setMinimumWidth(programArguments, 400);
    String message = ExecutionBundle.message("run.configuration.program.parameters.placeholder");
    programArguments.getEditorField().getEmptyText().setText(message);
    programArguments.getEditorField().getAccessibleContext().setAccessibleName(message);
    TextComponentEmptyText.setupPlaceholderVisibility(programArguments.getEditorField());
    setMonospaced(programArguments.getTextField());
    MacrosDialog.addMacroSupport(programArguments.getEditorField(), MacrosDialog.Filters.ALL, () -> myHasModule.compute() != null);
    SettingsEditorFragment<Settings, RawCommandLineEditor> parameters =
      new SettingsEditorFragment<>("commandLineParameters", ExecutionBundle.message("run.configuration.program.parameters.name"), null, programArguments,
                                   100,
                                   (settings, component) -> component.setText(settings.getProgramParameters()),
                                   (settings, component) -> settings.setProgramParameters(component.getText()),
                                   Predicates.alwaysTrue());
    parameters.setRemovable(false);
    parameters.setEditorGetter(editor -> editor.getEditorField());
    parameters.setHint(ExecutionBundle.message("run.configuration.program.parameters.hint"));
    return parameters;
  }

  public List<SettingsEditorFragment<Settings, ?>> getFragments() {
    return myFragments;
  }

  public <S extends InputRedirectAware> SettingsEditorFragment<S, ?> createRedirectFragment() {
    TextFieldWithBrowseButton inputFile = new TextFieldWithBrowseButton();
    inputFile.addActionListener(new ComponentWithBrowseButton.BrowseFolderActionListener<>(null, null, inputFile, null,
                                                                                           FileChooserDescriptorFactory
                                                                                             .createSingleFileDescriptor(),
                                                                                           TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT) {
      @Override
      protected @Nullable VirtualFile getInitialFile() {
        VirtualFile initialFile = super.getInitialFile();
        if (initialFile != null) {
          return initialFile;
        }
        String text = myWorkingDirectory.component().getComponent().getText();
        return LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(text));
      }
    });

    MacrosDialog.addMacroSupport((ExtendableTextField)inputFile.getTextField(), MacrosDialog.Filters.ALL, () -> myHasModule.compute() != null);
    LabeledComponent<TextFieldWithBrowseButton> labeledComponent =
      LabeledComponent.create(inputFile, ExecutionBundle.message("redirect.input.from"));
    labeledComponent.setLabelLocation(BorderLayout.WEST);
    SettingsEditorFragment<S, LabeledComponent<TextFieldWithBrowseButton>> redirectInput =
      new SettingsEditorFragment<>("redirectInput", ExecutionBundle.message("redirect.input.from.name"),
                                   ExecutionBundle.message("group.operating.system"), labeledComponent,
                                   (settings, component) -> component.getComponent().setText(
                                     FileUtil.toSystemDependentName(notNullize(settings.getInputRedirectOptions().getRedirectInputPath()))),
                                   (settings, component) -> {
                                     String filePath = component.getComponent().getText();
                                     settings.getInputRedirectOptions().setRedirectInput(component.isVisible() && isNotEmpty(filePath));
                                     settings.getInputRedirectOptions().setRedirectInputPath(
                                       isEmpty(filePath) ? null : FileUtil.toSystemIndependentName(filePath));
                                   },
                                   settings -> isNotEmpty(settings.getInputRedirectOptions().getRedirectInputPath()));
    redirectInput.setActionHint(ExecutionBundle.message("read.input.from.the.specified.file"));
    return redirectInput;
  }

  public static <S extends CommonProgramRunConfigurationParameters>
  SettingsEditorFragment<S, LabeledComponent<TextFieldWithBrowseButton>> createWorkingDirectory(
    @NotNull Project project,
    @NotNull Computable<? extends Module> moduleProvider
  ) {
    ExtendableTextField textField = new ExtendableTextField(10);
    MacrosDialog.addMacroSupport(textField, MacrosDialog.Filters.DIRECTORY_PATH, () -> moduleProvider.compute() != null);
    TextFieldWithBrowseButton workingDirectoryField = new TextFieldWithBrowseButton(textField);
    workingDirectoryField.addBrowseFolderListener(
      ExecutionBundle.message("select.working.directory.message"),
      null,
      project,
      FileChooserDescriptorFactory.createSingleFolderDescriptor(),
      TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
    );
    LabeledComponent<TextFieldWithBrowseButton> field = LabeledComponent.create(
      workingDirectoryField,
      ExecutionBundle.message("run.configuration.working.directory.label"),
      BorderLayout.WEST
    );
    SettingsEditorFragment<S, LabeledComponent<TextFieldWithBrowseButton>> workingDirectorySettings =
      new SettingsEditorFragment<>(
        "workingDirectory",
        ExecutionBundle.message("run.configuration.working.directory.name"),
        null,
        field,
        (settings, component) -> component.getComponent().setText(settings.getWorkingDirectory()),
        (settings, component) -> settings.setWorkingDirectory(component.getComponent().getText()),
        Predicates.alwaysTrue()
      );
    workingDirectorySettings.setRemovable(false);
    workingDirectorySettings.setValidation(
      settings -> {
        ThrowableRunnable<RuntimeConfigurationWarning> runnable =
          () -> ProgramParametersUtil.checkWorkingDirectoryExist(settings, project, moduleProvider.compute());
        ValidationInfo validationInfo = RuntimeConfigurationException.validate(textField, runnable);
        return Collections.singletonList(validationInfo);
      });
    return workingDirectorySettings;
  }

  public static <S extends CommonProgramRunConfigurationParameters> SettingsEditorFragment<S, ?> createEnvParameters() {
    EnvironmentVariablesComponent env = new EnvironmentVariablesComponent();
    env.setLabelLocation(BorderLayout.WEST);
    setMonospaced(env.getComponent().getTextField());
    SettingsEditorFragment<S, JComponent> fragment =
      new SettingsEditorFragment<>("environmentVariables", ExecutionBundle.message("environment.variables.fragment.name"),
                                   ExecutionBundle.message("group.operating.system"), env,
                                   (settings, c) -> env.reset(settings),
                                   (settings, c) -> {
                                     if (!env.isVisible()) {
                                       settings.setEnvs(Collections.emptyMap());
                                       settings.setPassParentEnvs(true);
                                     }
                                     else
                                       env.apply(settings);
                                   },
                                   Predicates.alwaysTrue());
    fragment.setCanBeHidden(true);
    fragment.setHint(ExecutionBundle.message("environment.variables.fragment.hint"));
    fragment.setActionHint(ExecutionBundle.message("set.custom.environment.variables.for.the.process"));
    fragment.addValidation(s -> checkEnvFiles(s));
    return fragment;
  }

  public static void setMonospaced(Component field) {
    field.setFont(EditorUtil.getEditorFont(JBUI.Fonts.label().getSize()));
  }


  public static <S> SettingsEditorFragment<S, JLabel> createHeader(@Nls(capitalization = Nls.Capitalization.Title) String title) {
    JLabel jLabel = new JLabel(title);
    jLabel.setFont(JBUI.Fonts.label().deriveFont(Font.BOLD));
    SettingsEditorFragment<S, JLabel> fragment = new SettingsEditorFragment<>(
      "title",
      null,
      null,
      jLabel,
      SettingsEditorFragmentType.HEADER,
      (__, ___) -> {},
      (__, ___) -> {},
      Predicates.alwaysTrue()
    );
    fragment.setCanBeHidden(false);
    fragment.setRemovable(false);
    return fragment;
  }

  public static <S> SettingsEditorFragment<S, JLabel> createRunHeader() {
    return createHeader(ExecutionBundle.message("application.configuration.title.run"));
  }
}
