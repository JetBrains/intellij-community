// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.openapi.roots.ui.configuration;

import com.intellij.idea.TestFor;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleConfigurationStateImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import java.awt.Component;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
public class ClasspathEditorTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR);

  @TestFor(issues = "IDEA-234383")
  @Test
  public void testJdkComboBoxWithNoSdk() {
    Project project = projectFixture.get().getProject();
    Module module = projectFixture.get().getModule();

    ModifiableRootModel mod = ModuleRootManager.getInstance(module).getModifiableModel();
    mod.setSdk(null);
    WriteAction.runAndWait(mod::commit);

    EdtTestUtil.runInEdtAndWait(() -> {
      final ModifiableRootModel uiRootModel = ModuleRootManager.getInstance(module).getModifiableModel();
      ClasspathEditor e = null;
      try {
        ModulesConfigurator modulesConfigurator = ProjectStructureConfigurable.getInstance(project).getContext().getModulesConfigurator();
        e = new ClasspathEditor(new ModuleConfigurationStateImpl(project, modulesConfigurator) {
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

        JdkComboBox box = findJdkComboBoxComponent(component);
        assertThat(box).isNotNull();
        assertThat(box.getSelectedJdk()).isNull();
        //this is the behaviour from 2019.3. The code does not support the situation, where no SDK is specified
        assertThat(box.getSelectedItem()).isInstanceOf(JdkComboBox.ProjectJdkComboBoxItem.class);
      }
      finally {
        if (e != null) {
          e.disposeUIResources();
        }
        ProjectStructureConfigurable.getInstance(project).getContext().getModulesConfigurator().disposeUIResources();
        uiRootModel.dispose();
      }
    });
  }

  @Nullable
  private static JdkComboBox findJdkComboBoxComponent(@NotNull Component component) {
    List<JdkComboBox> components = UIUtil.uiTraverser(component).filter(JdkComboBox.class).toList();
    assertThat(components).hasSize(1);
    return components.iterator().next();
  }
}