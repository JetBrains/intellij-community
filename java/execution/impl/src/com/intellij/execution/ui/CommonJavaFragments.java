// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.application.options.ModuleDescriptionsComboBox;
import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.CommonProgramRunConfigurationParameters;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

import static com.intellij.util.containers.ContainerUtil.exists;

public class CommonJavaFragments {

  public static <S extends CommonProgramRunConfigurationParameters> SettingsEditorFragment<S, ?> createEnvParameters() {
    EnvironmentVariablesComponent env = new EnvironmentVariablesComponent();
    env.setLabelLocation(BorderLayout.WEST);
    return SettingsEditorFragment.create("environmentVariables",
                                         ExecutionBundle.message("environment.variables.fragment.name"),
                                         ExecutionBundle.message("group.java.options"), env);
  }

  public static <S extends RunConfigurationBase<?>> SettingsEditorFragment<S, JLabel> createBuildBeforeRun() {
    String buildAndRun = ExecutionBundle.message("application.configuration.title.build.and.run");
    String run = ExecutionBundle.message("application.configuration.title.run");
    JLabel jLabel = new JLabel(buildAndRun);
    jLabel.setFont(JBUI.Fonts.label().deriveFont(Font.BOLD));
    return new RunConfigurationEditorFragment<S, JLabel>("buildBeforeRun",
                                                 ExecutionBundle.message("build.before.run"),
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
        if (isSelected()) {
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
        jLabel.setText(selected ? buildAndRun : run);
        fireEditorStateChanged();
      }

      @Override
      public boolean isSelected() {
        return buildAndRun.equals(jLabel.getText());
      }
    };
  }

  public static <S extends ModuleBasedConfiguration> SettingsEditorFragment<S, LabeledComponent<ModuleDescriptionsComboBox>> moduleClasspath(Project project) {
    ModuleDescriptionsComboBox comboBox = new ModuleDescriptionsComboBox();
    ConfigurationModuleSelector selector = new ConfigurationModuleSelector(project, comboBox);
    LabeledComponent<ModuleDescriptionsComboBox> component = LabeledComponent.create(comboBox, ExecutionBundle.message("use.module.classpath"));
    component.setLabelLocation(BorderLayout.WEST);
    return new SettingsEditorFragment<>("module.classpath",
                                        ExecutionBundle.message("application.configuration.use.classpath.and.jdk.of.module"),
                                        ExecutionBundle.message("group.java.options"),
                                        component, 0,
                                        (s, c) -> selector.reset(s),
                                        (s, c) -> selector.applyTo(s),
                                        s -> s.getConfigurationModule() != null);
  }
}
