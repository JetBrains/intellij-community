// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ui.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.execution.ui.CommandLinePanel.setMinimumWidth;

public final class JavaApplicationSettingsEditor extends JavaSettingsEditorBase<ApplicationConfiguration> {

  public JavaApplicationSettingsEditor(ApplicationConfiguration configuration) {
    super(configuration);
  }

  @Override
  protected void customizeFragments(List<SettingsEditorFragment<ApplicationConfiguration, ?>> fragments,
                                    ModuleClasspathCombo classpathCombo,
                                    CommonParameterFragments<ApplicationConfiguration> commonParameterFragments) {
    fragments.add(commonParameterFragments.programArguments());
    fragments.add(commonParameterFragments.createRedirectFragment());
    SettingsEditorFragment<ApplicationConfiguration, EditorTextField> mainClassFragment = createMainClass(classpathCombo);
    fragments.add(mainClassFragment);
    DefaultJreSelector jreSelector = DefaultJreSelector.fromSourceRootsDependencies(classpathCombo, mainClassFragment.component());
    SettingsEditorFragment<ApplicationConfiguration, JrePathEditor> jrePath = CommonJavaFragments.createJrePath(jreSelector);
    fragments.add(jrePath);
    fragments.add(createShortenClasspath(classpathCombo, jrePath, true));
  }

  @Override
  @NotNull
  protected SettingsEditorFragment<ApplicationConfiguration, ModuleClasspathCombo> createClasspathCombo() {
    ModuleClasspathCombo.Item item = new ModuleClasspathCombo.Item(ExecutionBundle.message("application.configuration.include.provided.scope"));
    return CommonJavaFragments.moduleClasspath(item, configuration -> configuration.getOptions().isIncludeProvidedScope(),
                                               (configuration, value) -> configuration.getOptions().setIncludeProvidedScope(value));
  }

  @NotNull
  private SettingsEditorFragment<ApplicationConfiguration, EditorTextField> createMainClass(ModuleClasspathCombo classpathCombo) {
    EditorTextField mainClass = ClassEditorField.createClassField(getProject(), () -> classpathCombo.getSelectedModule(),
                                                                  JavaCodeFragment.VisibilityChecker.PROJECT_SCOPE_VISIBLE, null);
    mainClass.setShowPlaceholderWhenFocused(true);
    CommonParameterFragments.setMonospaced(mainClass);
    String placeholder = ExecutionBundle.message("application.configuration.main.class.placeholder");
    mainClass.setPlaceholder(placeholder);
    mainClass.getAccessibleContext().setAccessibleName(placeholder);
    setMinimumWidth(mainClass, 300);
    SettingsEditorFragment<ApplicationConfiguration, EditorTextField> mainClassFragment =
      new SettingsEditorFragment<>("mainClass", ExecutionBundle.message("application.configuration.main.class"), null, mainClass, 20,
                                   (configuration, component) -> component.setText(configuration.getMainClassName()),
                                   (configuration, component) -> configuration.setMainClassName(component.getText()),
                                   configuration -> true);
    mainClassFragment.setHint(ExecutionBundle.message("application.configuration.main.class.hint"));
    mainClassFragment.setRemovable(false);
    mainClassFragment.setEditorGetter(field -> {
      Editor editor = field.getEditor();
      return editor == null ? field : editor.getContentComponent();
    });
    return mainClassFragment;
  }
}
