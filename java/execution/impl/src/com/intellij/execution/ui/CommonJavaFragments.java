// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import static com.intellij.execution.ui.CommandLinePanel.setMinimumWidth;
import static com.intellij.util.containers.ContainerUtil.exists;

public class CommonJavaFragments {

  public static <S extends RunConfigurationBase<?>> SettingsEditorFragment<S, JLabel> createBuildBeforeRun(BeforeRunComponent beforeRunComponent) {
    String buildAndRun = ExecutionBundle.message("application.configuration.title.build.and.run");
    String run = ExecutionBundle.message("application.configuration.title.run");
    JLabel jLabel = new JLabel(buildAndRun);
    jLabel.setFont(JBUI.Fonts.label().deriveFont(Font.BOLD));
    RunConfigurationEditorFragment<S, JLabel> fragment = new RunConfigurationEditorFragment<S, JLabel>("doNotBuildBeforeRun",
                                                                                                       ExecutionBundle.message("do.not.build.before.run"),
                                                                                                       ExecutionBundle.message("group.java.options"),
                                                                                                       jLabel, -1) {
      @Override
      public void resetEditorFrom(@NotNull RunnerAndConfigurationSettingsImpl s) {
        jLabel.setText(hasTask(s) ? buildAndRun : run);
      }

      private boolean hasTask(@NotNull RunnerAndConfigurationSettingsImpl s) {
        return exists(s.getManager().getBeforeRunTasks(s.getConfiguration()),
                      t -> CompileStepBeforeRun.ID == t.getProviderId());
      }

      @Override
      public void applyEditorTo(@NotNull RunnerAndConfigurationSettingsImpl s) {
        ArrayList<BeforeRunTask<?>> tasks = new ArrayList<>(s.getManager().getBeforeRunTasks(s.getConfiguration()));
        if (!isSelected()) {
          if (!hasTask(s)) {
            CompileStepBeforeRun.MakeBeforeRunTask task =
              new CompileStepBeforeRun.MakeBeforeRunTask();
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
    beforeRunComponent.setTagListener((key, added) -> {
      if (CompileStepBeforeRun.ID == key) {
        jLabel.setText(added ? buildAndRun : run);
      }
    });
    return fragment;
  }

  public static <S extends ModuleBasedConfiguration<?,?>> SettingsEditorFragment<S, ModuleClasspathCombo> moduleClasspath(
    ModuleClasspathCombo.Item option, Predicate<S> getter, BiConsumer<S, Boolean> setter) {
    ModuleClasspathCombo comboBox = new ModuleClasspathCombo(option);
    setMinimumWidth(comboBox, 400);
    return new SettingsEditorFragment<>("module.classpath",
                                        ExecutionBundle.message("application.configuration.use.classpath.and.jdk.of.module"),
                                        ExecutionBundle.message("group.java.options"),
                                        comboBox, 10,
                                        (s, c) -> { comboBox.reset(s); option.myOptionValue = getter.test(s); },
                                        (s, c) -> {
                                          if (comboBox.isVisible()) {
                                            comboBox.applyTo(s);
                                            setter.accept(s, option.myOptionValue);
                                          }
                                          else {
                                            s.setModule(s.getDefaultModule());
                                            setter.accept(s, false);
                                          }
                                        },
                                        s -> s.getDefaultModule() != s.getConfigurationModule().getModule());
  }

  @NotNull
  public static SettingsEditorFragment<ApplicationConfiguration, JrePathEditor> createJrePath(Project project) {
    JrePathEditor jrePathEditor = new JrePathEditor(false);
    ComboBox<JrePathEditor.JreComboBoxItem> comboBox = jrePathEditor.getComponent();
    comboBox.setRenderer(new ColoredListCellRenderer<JrePathEditor.JreComboBoxItem>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends JrePathEditor.JreComboBoxItem> list,
                                           JrePathEditor.JreComboBoxItem value,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        if (value != null) {
          if (index == -1) {
            append("java ");
            append("(" + value.getPresentableText() + ")");
          }
          else value.render(this, selected);
        }
      }
    });

    setMinimumWidth(jrePathEditor, 100);
    jrePathEditor.getLabel().setVisible(false);
    jrePathEditor.setDefaultJreSelector(DefaultJreSelector.projectSdk(project));
    return new SettingsEditorFragment<>("jrePath", null, null, jrePathEditor, 5,
                                        (configuration, editor) -> editor
                                          .setPathOrName(configuration.getAlternativeJrePath(),
                                                         configuration.isAlternativeJrePathEnabled()),
                                        (configuration, editor) -> {
                                          configuration.setAlternativeJrePath(editor.getJrePathOrName());
                                          configuration.setAlternativeJrePathEnabled(editor.isAlternativeJreSelected());
                                        },
                                        configuration -> true);
  }
}
