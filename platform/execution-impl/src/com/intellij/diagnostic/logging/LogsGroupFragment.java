// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.logging;

import com.intellij.diagnostic.DiagnosticBundle;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.ui.NestedGroupFragment;
import com.intellij.execution.ui.SettingsEditorFragment;
import com.intellij.openapi.fileChooser.FileSaverDescriptorFactory;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

public final class LogsGroupFragment<T extends RunConfigurationBase<?>> extends NestedGroupFragment<T> {
  public LogsGroupFragment() {
    super("log",
          DiagnosticBundle.message("log.monitor.fragment.name"), DiagnosticBundle.message("log.monitor.fragment.group"),
          t -> false);
    setActionHint(ExecutionBundle.message("the.ide.will.display.the.selected.logs.in.the.run.tool.window"));
  }

  @Override
  protected List<SettingsEditorFragment<T, ?>> createChildren() {
    var myOutputFile = new TextFieldWithBrowseButton();
    var descriptor = FileSaverDescriptorFactory.createSingleFileNoJarsDescriptor()
      .withTitle(ExecutionBundle.message("choose.file.to.save.console.output"))
      .withDescription(ExecutionBundle.message("console.output.would.be.saved.to.the.specified.file"));
    myOutputFile.addFileSaverDialog(null, descriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    LabeledComponent<TextFieldWithBrowseButton> component =
      LabeledComponent.create(myOutputFile, ExecutionBundle.message("save.output.console.to.file"), BorderLayout.WEST);
    SettingsEditorFragment<T, LabeledComponent<TextFieldWithBrowseButton>> fragment =
      new SettingsEditorFragment<>("logs.save.output", ExecutionBundle.message("save.output.console.to.file.option"), null, component,
                                   (t, c) -> c.getComponent().setText(StringUtil.notNullize(t.getOutputFilePath())),
                                   (t, c) -> {
                                     t.setFileOutputPath(c.getComponent().getText());
                                     t.setSaveOutputToFile(c.isVisible() && StringUtil.isNotEmpty(component.getComponent().getText()));
                                   },
                                   t -> t.isSaveOutputToFile());
    fragment.setActionHint(ExecutionBundle.message("write.the.output.of.the.application.to.a.file.for.later.inspection"));
    SettingsEditorFragment<T, ?> stdOut = SettingsEditorFragment
      .createTag("logs.stdout", DiagnosticBundle.message("log.monitor.fragment.stdout"), null, t -> t.isShowConsoleOnStdOut(),
                 (t, value) -> t.setShowConsoleOnStdOut(value));
    stdOut.setActionHint(ExecutionBundle.message("activate.the.console.when.the.application.writes.to.the.standard.output.stream"));
    SettingsEditorFragment<T, ?> stdErr = SettingsEditorFragment
      .createTag("logs.stderr", DiagnosticBundle.message("log.monitor.fragment.stderr"), null, t -> t.isShowConsoleOnStdErr(),
                 (t, value) -> t.setShowConsoleOnStdErr(value));
    stdErr.setActionHint(ExecutionBundle.message("activate.the.console.when.the.application.writes.to.the.standard.error.stream"));
    return Arrays.asList(new LogsFragment<>(), fragment, stdOut, stdErr);
  }

  @Override
  public String getChildrenGroupName() {
    return DiagnosticBundle.message("log.monitor.fragment.settings");
  }
}
