// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.target.TargetEnvironmentAwareRunProfile;
import com.intellij.execution.target.TargetEnvironmentConfigurations;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.SmartList;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

import static com.intellij.execution.ui.CommandLinePanel.setMinimumWidth;
import static com.intellij.util.containers.ContainerUtil.exists;

public final class CommonJavaFragments {

  public static final String JRE_PATH = "jrePath";

  private static boolean hasTask(@NotNull RunnerAndConfigurationSettingsImpl s) {
    return exists(s.getManager().getBeforeRunTasks(s.getConfiguration()), t -> CompileStepBeforeRun.ID == t.getProviderId());
  }

  public static <S extends RunConfigurationBase<?>> SettingsEditorFragment<S, JLabel> createBuildBeforeRun(BeforeRunComponent beforeRunComponent,
                                                                                                           SettingsEditor<S> settingsEditor) {
    String buildAndRun = ExecutionBundle.message("application.configuration.title.build.and.run");
    String run = ExecutionBundle.message("application.configuration.title.run");
    JLabel jLabel = new JLabel(buildAndRun);
    jLabel.setFont(JBUI.Fonts.label().deriveFont(Font.BOLD));
    RunConfigurationEditorFragment<S, JLabel> fragment = new RunConfigurationEditorFragment<>("doNotBuildBeforeRun",
                                                                                              ExecutionBundle
                                                                                                .message("do.not.build.before.run"),
                                                                                              ExecutionBundle.message("group.java.options"),
                                                                                              jLabel, -1,
                                                                                              settings -> !hasTask(settings)) {
      @Override
      public void doReset(@NotNull RunnerAndConfigurationSettingsImpl s) {
        jLabel.setText(hasTask(s) ? buildAndRun : run);
      }

      @Override
      public void applyEditorTo(@NotNull RunnerAndConfigurationSettingsImpl s) {
        ArrayList<BeforeRunTask<?>> tasks = new ArrayList<>(s.getManager().getBeforeRunTasks(s.getConfiguration()));
        if (!isSelected()) {
          if (!hasTask(s)) {
            CompileStepBeforeRun.MakeBeforeRunTask task = new CompileStepBeforeRun.MakeBeforeRunTask();
            task.setEnabled(true);
            tasks.add(task);
          }
        }
        else {
          tasks.removeIf(t -> CompileStepBeforeRun.ID == t.getProviderId());
        }
        s.getManager().setBeforeRunTasks(s.getConfiguration(), tasks);
      }

      @Override
      public void setSelected(boolean selected) {
        jLabel.setText(selected ? run : buildAndRun);
        beforeRunComponent.addOrRemove(CompileStepBeforeRun.ID, !selected);
        fireEditorStateChanged();
      }

      @Override
      public boolean isSelected() {
        return run.equals(jLabel.getText());
      }

      @Override
      protected @NotNull JComponent createEditor() {
        return myComponent;
      }
    };
    settingsEditor.addSettingsEditorListener(editor -> jLabel.setText(beforeRunComponent.hasEnabledTask(CompileStepBeforeRun.ID) ? buildAndRun : run));
    fragment.setActionHint(ExecutionBundle.message("run.the.application.without.launching.the.build.process"));
    return fragment;
  }

  public static <S extends ModuleBasedConfiguration<?,?>> SettingsEditorFragment<S, ModuleClasspathCombo> moduleClasspath() {
    ModuleClasspathCombo comboBox = new ModuleClasspathCombo();
    String name = ExecutionBundle.message("application.configuration.use.classpath.and.jdk.of.module");
    comboBox.getAccessibleContext().setAccessibleName(name);
    setMinimumWidth(comboBox, 400);
    CommonParameterFragments.setMonospaced(comboBox);
    SettingsEditorFragment<S, ModuleClasspathCombo> fragment =
      new SettingsEditorFragment<>("module.classpath", name, ExecutionBundle.message("group.java.options"), comboBox, 10,
                                   (s, c) -> comboBox.reset(s),
                                   (s, c) -> {
                                     if (comboBox.isVisible()) {
                                       comboBox.applyTo(s);
                                     }
                                     else {
                                       s.setModule(s.getDefaultModule());
                                     }
                                   },
                                   s -> ModuleManager.getInstance(s.getProject()).getModules().length > 1);
    fragment.setHint(ExecutionBundle.message("application.configuration.use.classpath.and.jdk.of.module.hint"));
    fragment.setActionHint(
      ExecutionBundle.message("the.module.whose.classpath.will.be.used.the.classpath.specified.in.the.vm.options.takes.precedence.over.this.one"));
    return fragment;
  }

  @NotNull
  public static <T extends CommonJavaRunConfigurationParameters> SettingsEditorFragment<T, JrePathEditor> createJrePath(DefaultJreSelector defaultJreSelector) {
    JrePathEditor jrePathEditor = new JrePathEditor(false);
    jrePathEditor.setDefaultJreSelector(defaultJreSelector);
    ComboBox<JrePathEditor.JreComboBoxItem> comboBox = jrePathEditor.getComponent();
    comboBox.setRenderer(new ColoredListCellRenderer<>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends JrePathEditor.JreComboBoxItem> list,
                                           JrePathEditor.JreComboBoxItem value,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        if (value == null) {
          return;
        }

        if (BundledJreProvider.BUNDLED.equals(value.getPresentableText())) {
          if (index == -1) append("java "); //NON-NLS
          append(ExecutionBundle.message("bundled.jre.name"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
          return;
        }

        if (value.getPathOrName() == null && value.getVersion() == null) {
          append(StringUtil.notNullize(value.getDescription()));
          return;
        }
        if (index == -1) {
          append("java "); //NON-NLS
          String shortVersion = appendShortVersion(value);
          if (value.getPathOrName() != null && !value.getPathOrName().equals(shortVersion)) {
            append(value.getPathOrName() + " ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
          else if (value.getDescription() != null) {
            append(value.getDescription() + " ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
        }
        else {
          if (value.getPathOrName() != null) {
            append(value.getPathOrName() + " ");
          }
          else {
            appendShortVersion(value);
          }
          if (value.getDescription() != null) {
            append(value.getDescription() + " ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
        }
      }

      private @Nullable @NlsSafe String appendShortVersion(JrePathEditor.JreComboBoxItem value) {
        if (value.getVersion() != null) {
          JavaSdkVersion version = JavaSdkVersion.fromVersionString(value.getVersion());
          if (version != null) {
            append(version.getDescription() + " ");
            return version.getDescription();
          }
        }
        return null;
      }
    });
    CommonParameterFragments.setMonospaced(comboBox);

    Dimension minimumSize = setMinimumWidth(jrePathEditor, 200);
    jrePathEditor.setPreferredSize(minimumSize);
    jrePathEditor.getLabel().setVisible(false);
    jrePathEditor.getComponent().getAccessibleContext().setAccessibleName(jrePathEditor.getLabel().getText());
    SettingsEditorFragment<T, JrePathEditor> jrePath =
      new SettingsEditorFragment<>(JRE_PATH, ExecutionBundle.message("run.configuration.jre.name"), null, jrePathEditor, 5,
                                   (configuration, editor) -> editor.setPathOrName(configuration.getAlternativeJrePath(),
                                                                                   configuration.isAlternativeJrePathEnabled()),
                                   (configuration, editor) -> {
                                     configuration.setAlternativeJrePath(editor.getJrePathOrName());
                                     configuration.setAlternativeJrePathEnabled(editor.isAlternativeJreSelected());
                                   },
                                   configuration -> true);
    jrePath.setRemovable(false);
    jrePath.setHint(ExecutionBundle.message("run.configuration.jre.hint"));
    jrePath.setValidation(configuration -> new SmartList<>(RuntimeConfigurationException.validate(comboBox, () -> {
      if (!(configuration instanceof TargetEnvironmentAwareRunProfile) ||
          TargetEnvironmentConfigurations.getEffectiveTargetName((TargetEnvironmentAwareRunProfile)configuration,
                                                                 configuration.getProject()) == null) {
        JavaParametersUtil.checkAlternativeJRE(configuration);
      }
    })));
    return jrePath;
  }
}
