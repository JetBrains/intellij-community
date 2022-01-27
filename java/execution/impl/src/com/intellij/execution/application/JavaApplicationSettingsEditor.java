// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.application;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.ui.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.intellij.execution.ui.CommandLinePanel.setMinimumWidth;

public final class JavaApplicationSettingsEditor extends JavaSettingsEditorBase<ApplicationConfiguration> {

  public JavaApplicationSettingsEditor(ApplicationConfiguration configuration) {
    super(configuration);
  }

  @Override
  public boolean isInplaceValidationSupported() {
    return true;
  }

  @Override
  protected void customizeFragments(List<SettingsEditorFragment<ApplicationConfiguration, ?>> fragments,
                                    SettingsEditorFragment<ApplicationConfiguration, ModuleClasspathCombo> moduleClasspath,
                                    CommonParameterFragments<ApplicationConfiguration> commonParameterFragments) {
    fragments.add(SettingsEditorFragment.createTag("include.provided",
                                                   ExecutionBundle.message("application.configuration.include.provided.scope"),
                                                   ExecutionBundle.message("group.java.options"),
                                     configuration -> configuration.getOptions().isIncludeProvidedScope(),
                                     (configuration, value) -> configuration.getOptions().setIncludeProvidedScope(value)));
    fragments.add(commonParameterFragments.programArguments());
    fragments.add(new TargetPathFragment<>());
    fragments.add(commonParameterFragments.createRedirectFragment());
    SettingsEditorFragment<ApplicationConfiguration, EditorTextField> mainClassFragment = createMainClass(moduleClasspath.component());
    fragments.add(mainClassFragment);
    DefaultJreSelector jreSelector = DefaultJreSelector.fromSourceRootsDependencies(moduleClasspath.component(), mainClassFragment.component());
    SettingsEditorFragment<ApplicationConfiguration, JrePathEditor> jrePath = CommonJavaFragments.createJrePath(jreSelector);
    fragments.add(jrePath);
    fragments.add(createShortenClasspath(moduleClasspath.component(), jrePath, true));
  }

  @NotNull
  private SettingsEditorFragment<ApplicationConfiguration, EditorTextField> createMainClass(ModuleClasspathCombo classpathCombo) {
    ConfigurationModuleSelector moduleSelector = new ConfigurationModuleSelector(getProject(), classpathCombo);
    EditorTextField mainClass = ClassEditorField.createClassField(getProject(), () -> classpathCombo.getSelectedModule(),
                                                                  ApplicationConfigurable.getVisibilityChecker(moduleSelector), null);
    mainClass.setBackground(UIUtil.getTextFieldBackground());
    mainClass.setShowPlaceholderWhenFocused(true);
    CommonParameterFragments.setMonospaced(mainClass);
    String placeholder = ExecutionBundle.message("application.configuration.main.class.placeholder");
    mainClass.setPlaceholder(placeholder);
    mainClass.getAccessibleContext().setAccessibleName(placeholder);
    setMinimumWidth(mainClass, 300);
    SettingsEditorFragment<ApplicationConfiguration, EditorTextField> mainClassFragment =
      new SettingsEditorFragment<>("mainClass", ExecutionBundle.message("application.configuration.main.class"), null, mainClass, 20,
                                   (configuration, component) -> component.setText(getQName(configuration.getMainClassName())),
                                   (configuration, component) -> {
                                     String className = component.getText();
                                     if (!className.equals(configuration.getMainClassName())) {
                                       configuration.setMainClassName(getJvmName(className));
                                     }
                                   },
                                   configuration -> true);
    mainClassFragment.setHint(ExecutionBundle.message("application.configuration.main.class.hint"));
    mainClassFragment.setRemovable(false);
    mainClassFragment.setEditorGetter(field -> {
      Editor editor = field.getEditor();
      return editor == null ? field : editor.getContentComponent();
    });
    mainClassFragment.setValidation((configuration) ->
      Collections.singletonList(RuntimeConfigurationException.validate(mainClass, () -> configuration.checkClass())));
    return mainClassFragment;
  }

  @Nullable
  private String getQName(@Nullable String className) {
    if (className == null || className.indexOf('$') < 0) return className;
    PsiClass psiClass = FileBasedIndex.getInstance().ignoreDumbMode(DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE, () -> ClassUtil
      .findPsiClass(PsiManager.getInstance(getProject()), className));
    return psiClass == null ? className : psiClass.getQualifiedName();
  }

  @Nullable
  private String getJvmName(@Nullable String className) {
    if (className == null) return null;
    return FileBasedIndex.getInstance().ignoreDumbMode(DumbModeAccessType.RELIABLE_DATA_ONLY, () -> {
      PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass(className, GlobalSearchScope.allScope(getProject()));
      return aClass != null ? JavaExecutionUtil.getRuntimeQualifiedName(aClass) : className;
    });
  }
}
