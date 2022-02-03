// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.application;

import com.intellij.application.options.ModuleDescriptionsComboBox;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.ConfigurationUtil;
import com.intellij.execution.impl.SingleConfigurationConfigurable;
import com.intellij.execution.ui.*;
import com.intellij.ide.DataManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.EditorTextFieldWithBrowseButton;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ApplicationConfigurable extends SettingsEditor<ApplicationConfiguration> implements PanelWithAnchor {
  private final Project myProject;
  private final ConfigurationModuleSelector myModuleSelector;

  private JPanel myWholePanel;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myMainClass;
  private CommonJavaParametersPanel myCommonProgramParameters;
  private LabeledComponent<ModuleDescriptionsComboBox> myModule;
  private LabeledComponent<JBCheckBox> myIncludeProvidedDeps;
  private JrePathEditor myJrePathEditor;
  private LabeledComponent<ShortenCommandLineModeCombo> myShortenClasspathModeCombo;
  private JComponent myAnchor;

  public ApplicationConfigurable(@NotNull Project project) {
    myProject = project;
    myModuleSelector = new ConfigurationModuleSelector(project, myModule.getComponent());

    myJrePathEditor.setDefaultJreSelector(DefaultJreSelector.fromSourceRootsDependencies(myModule.getComponent(), getMainClassField()));
    myCommonProgramParameters.setModuleContext(myModuleSelector.getModule());
    myModule.getComponent().addActionListener(e -> myCommonProgramParameters.setModuleContext(myModuleSelector.getModule()));
    new ClassBrowser.AppClassBrowser<EditorTextField>(project, myModuleSelector).setField(getMainClassField());
    myShortenClasspathModeCombo.setComponent(new ShortenCommandLineModeCombo(myProject, myJrePathEditor, myModule.getComponent()));
    myIncludeProvidedDeps.setComponent(new JBCheckBox(ExecutionBundle.message("application.configuration.include.provided.scope")));

    myAnchor = UIUtil.mergeComponentsWithAnchor(myMainClass, myCommonProgramParameters, myJrePathEditor, myModule,
                                                myShortenClasspathModeCombo, myIncludeProvidedDeps);
  }

  @Override
  public void applyEditorTo(@NotNull ApplicationConfiguration configuration) throws ConfigurationException {
    myCommonProgramParameters.applyTo(configuration);
    myModuleSelector.applyTo(configuration);

    String className = getMainClassField().getText();
    if (!className.equals(getInitialMainClassName(configuration))) {
      PsiClass aClass = myModuleSelector.findClass(className);
      configuration.setMainClassName(aClass != null ? JavaExecutionUtil.getRuntimeQualifiedName(aClass) : className);
    }
    configuration.setAlternativeJrePath(myJrePathEditor.getJrePathOrName());
    configuration.setAlternativeJrePathEnabled(myJrePathEditor.isAlternativeJreSelected());
    configuration.setShortenCommandLine(myShortenClasspathModeCombo.getComponent().getSelectedItem());
    configuration.setIncludeProvidedScope(myIncludeProvidedDeps.getComponent().isSelected());

    hideUnsupportedFieldsIfNeeded();
  }

  public void hideUnsupportedFieldsIfNeeded() {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      boolean localTarget = DataManager.getInstance().getDataContext(myWholePanel)
                              .getData(SingleConfigurationConfigurable.RUN_ON_TARGET_NAME_KEY) == null;
      myJrePathEditor.setVisible(localTarget);
    }
  }

  @Override
  public void resetEditorFrom(@NotNull ApplicationConfiguration configuration) {
    myCommonProgramParameters.reset(configuration);
    myModuleSelector.reset(configuration);

    getMainClassField().setText(getInitialMainClassName(configuration));
    myJrePathEditor.setPathOrName(configuration.getAlternativeJrePath(), configuration.isAlternativeJrePathEnabled());
    myShortenClasspathModeCombo.getComponent().setSelectedItem(configuration.getShortenCommandLine());
    myIncludeProvidedDeps.getComponent().setSelected(configuration.isProvidedScopeIncluded());

    hideUnsupportedFieldsIfNeeded();
  }

  @NotNull
  private String getInitialMainClassName(@NotNull ApplicationConfiguration configuration) {
    return configuration.getMainClassName() != null ? configuration.getMainClassName().replaceAll("\\$", "\\.") : "";
  }

  public EditorTextFieldWithBrowseButton getMainClassField() {
    return myMainClass.getComponent();
  }

  public CommonJavaParametersPanel getCommonProgramParameters() {
    return myCommonProgramParameters;
  }

  @NotNull
  @Override
  public JComponent createEditor() {
    return myWholePanel;
  }

  private void createUIComponents() {
    myMainClass = new LabeledComponent<>();
    JavaCodeFragment.VisibilityChecker visibilityChecker = getVisibilityChecker(myModuleSelector);
    myMainClass.setComponent(new EditorTextFieldWithBrowseButton(myProject, true, visibilityChecker));
    myShortenClasspathModeCombo = new LabeledComponent<>();
  }

  @NotNull
  static JavaCodeFragment.VisibilityChecker getVisibilityChecker(ConfigurationModuleSelector selector) {
    return (declaration, place) -> {
      if (declaration instanceof PsiClass) {
        PsiClass aClass = (PsiClass)declaration;
        if (ConfigurationUtil.MAIN_CLASS.value(aClass) && PsiMethodUtil.findMainMethod(aClass) != null ||
            place.getParent() != null && selector.findClass(((PsiClass)declaration).getQualifiedName()) != null) {
          return JavaCodeFragment.VisibilityChecker.Visibility.VISIBLE;
        }
      }
      return JavaCodeFragment.VisibilityChecker.Visibility.NOT_VISIBLE;
    };
  }

  @Override
  public JComponent getAnchor() {
    return myAnchor;
  }

  @Override
  public void setAnchor(@Nullable JComponent anchor) {
    myAnchor = anchor;
    myMainClass.setAnchor(anchor);
    myCommonProgramParameters.setAnchor(anchor);
    myJrePathEditor.setAnchor(anchor);
    myModule.setAnchor(anchor);
    myShortenClasspathModeCombo.setAnchor(anchor);
  }
}