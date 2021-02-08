// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.idea.TestFor;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleConfigurationStateImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ClasspathEditorTest extends LightPlatformTestCase {

  @TestFor(issues = "IDEA-234383")
  public void testJdkComboBoxWithNoSdk() {
    Project project = getProject();
    Module module = getModule();

    ModifiableRootModel mod = ModuleRootManager.getInstance(module).getModifiableModel();
    mod.setSdk(null);
    WriteAction.runAndWait(mod::commit);

    final ModifiableRootModel uiRootModel = ModuleRootManager.getInstance(module).getModifiableModel();
    disposeOnTearDown(() -> uiRootModel.dispose());

    ModulesConfigurator modulesConfigurator = ProjectStructureConfigurable.getInstance(project).getContext().getModulesConfigurator();
    ClasspathEditor e = new ClasspathEditor(new ModuleConfigurationStateImpl(project, modulesConfigurator) {
      @Override
      public ModifiableRootModel getModifiableRootModel() {
        return uiRootModel;
      }

      @Override
      public ModuleRootModel getCurrentRootModel() {
        return uiRootModel;
      }
    });

    e.reset();
    JComponent component = e.createComponent();
    disposeOnTearDown(() -> e.disposeUIResources());

    JdkComboBox box = findJdkComboBoxComponent(component);
    assertThat(box).isNotNull();
    assertThat(box.getSelectedJdk()).isNull();
    //this is the behaviour from 2019.3. The code does not support the situation, where no SDK is specified
    assertThat(box.getSelectedItem()).isInstanceOf(JdkComboBox.ProjectJdkComboBoxItem.class);
  }

  @Nullable
  private static JdkComboBox findJdkComboBoxComponent(@NotNull Component component) {
    List<JdkComboBox> components = UIUtil.uiTraverser(component).filter(JdkComboBox.class).toList();
    assertThat(components).hasSize(1);
    return components.iterator().next();
  }
}