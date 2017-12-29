/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.application;

import com.intellij.application.options.ModuleDescriptionsComboBox;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.ShortenCommandLine;
import com.intellij.execution.configurations.ConfigurationUtil;
import com.intellij.execution.ui.*;
import com.intellij.execution.util.JreVersionDetector;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.ui.EditorTextFieldWithBrowseButton;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ApplicationConfigurable extends SettingsEditor<ApplicationConfiguration> implements PanelWithAnchor {
  private CommonJavaParametersPanel myCommonProgramParameters;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myMainClass;
  private LabeledComponent<ModuleDescriptionsComboBox> myModule;
  private LabeledComponent<ShortenCommandLineModeCombo> myShortenClasspathModeCombo;
  private JPanel myWholePanel;

  private final ConfigurationModuleSelector myModuleSelector;
  private JrePathEditor myJrePathEditor;
  private JCheckBox myShowSwingInspectorCheckbox;
  private LabeledComponent<JBCheckBox> myIncludeProvidedDeps;
  private final JreVersionDetector myVersionDetector;
  private final Project myProject;
  private JComponent myAnchor;

  public ApplicationConfigurable(final Project project) {
    myProject = project;
    myModuleSelector = new ConfigurationModuleSelector(project, myModule.getComponent());
    myJrePathEditor.setDefaultJreSelector(DefaultJreSelector.fromSourceRootsDependencies(myModule.getComponent(), getMainClassField()));
    myCommonProgramParameters.setModuleContext(myModuleSelector.getModule());
    myModule.getComponent().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myCommonProgramParameters.setModuleContext(myModuleSelector.getModule());
      }
    });
    ClassBrowser.createApplicationClassBrowser(project, myModuleSelector).setField(getMainClassField());
    myVersionDetector = new JreVersionDetector();

    myShortenClasspathModeCombo.setComponent(new ShortenCommandLineModeCombo(myProject, myJrePathEditor, myModule.getComponent()));
    myIncludeProvidedDeps.setComponent(new JBCheckBox(ExecutionBundle.message("application.configuration.include.provided.scope")));
    myAnchor = UIUtil.mergeComponentsWithAnchor(myMainClass, myCommonProgramParameters, myJrePathEditor, myModule,
                                                myShortenClasspathModeCombo, myIncludeProvidedDeps);
  }

  public void applyEditorTo(@NotNull final ApplicationConfiguration configuration) throws ConfigurationException {
    myCommonProgramParameters.applyTo(configuration);
    myModuleSelector.applyTo(configuration);
    final String className = getMainClassField().getText();
    final PsiClass aClass = myModuleSelector.findClass(className);
    configuration.setMainClassName(aClass != null ? JavaExecutionUtil.getRuntimeQualifiedName(aClass) : className);
    configuration.setAlternativeJrePath(myJrePathEditor.getJrePathOrName());
    configuration.setAlternativeJrePathEnabled(myJrePathEditor.isAlternativeJreSelected());
    configuration.setSwingInspectorEnabled((myVersionDetector.isJre50Configured(configuration) || myVersionDetector.isModuleJre50Configured(configuration)) && myShowSwingInspectorCheckbox.isSelected());
    configuration.setShortenCommandLine((ShortenCommandLine)myShortenClasspathModeCombo.getComponent().getSelectedItem());
    configuration.setIncludeProvidedScope(myIncludeProvidedDeps.getComponent().isSelected());

    updateShowSwingInspector(configuration);
  }

  public void resetEditorFrom(@NotNull final ApplicationConfiguration configuration) {
    myCommonProgramParameters.reset(configuration);
    myModuleSelector.reset(configuration);

    getMainClassField().setText(configuration.getMainClassName() != null ? configuration.getMainClassName().replaceAll("\\$", "\\.") : "");
    myJrePathEditor.setPathOrName(configuration.getAlternativeJrePath(), configuration.isAlternativeJrePathEnabled());
    myShortenClasspathModeCombo.getComponent().setSelectedItem(configuration.getShortenCommandLine());
    myIncludeProvidedDeps.getComponent().setSelected(configuration.isProvidedScopeIncluded());

    updateShowSwingInspector(configuration);
  }

  private void updateShowSwingInspector(final ApplicationConfiguration configuration) {
    if (myVersionDetector.isJre50Configured(configuration) || myVersionDetector.isModuleJre50Configured(configuration)) {
      myShowSwingInspectorCheckbox.setEnabled(true);
      myShowSwingInspectorCheckbox.setSelected(configuration.isSwingInspectorEnabled());
      myShowSwingInspectorCheckbox.setText(ExecutionBundle.message("show.swing.inspector"));
    }
    else {
      myShowSwingInspectorCheckbox.setEnabled(false);
      myShowSwingInspectorCheckbox.setSelected(false);
      myShowSwingInspectorCheckbox.setText(ExecutionBundle.message("show.swing.inspector.disabled"));
    }
  }

  public EditorTextFieldWithBrowseButton getMainClassField() {
    return myMainClass.getComponent();
  }

  public CommonJavaParametersPanel getCommonProgramParameters() {
    return myCommonProgramParameters;
  }

  @NotNull
  public JComponent createEditor() {
    return myWholePanel;
  }

  private void createUIComponents() {
    myMainClass = new LabeledComponent<>();
    myMainClass.setComponent(new EditorTextFieldWithBrowseButton(myProject, true, new JavaCodeFragment.VisibilityChecker() {
      @Override
      public Visibility isDeclarationVisible(PsiElement declaration, PsiElement place) {
        if (declaration instanceof PsiClass) {
          final PsiClass aClass = (PsiClass)declaration;
          if (ConfigurationUtil.MAIN_CLASS.value(aClass) && PsiMethodUtil.findMainMethod(aClass) != null || place.getParent() != null && myModuleSelector.findClass(((PsiClass)declaration).getQualifiedName()) != null) {
            return Visibility.VISIBLE;
          }
        }
        return Visibility.NOT_VISIBLE;
      }
    }));
    myShortenClasspathModeCombo = new LabeledComponent<>();
  }

  @Override
  public JComponent getAnchor() {
    return myAnchor;
  }

  @Override
  public void setAnchor(@Nullable JComponent anchor) {
    this.myAnchor = anchor;
    myMainClass.setAnchor(anchor);
    myCommonProgramParameters.setAnchor(anchor);
    myJrePathEditor.setAnchor(anchor);
    myModule.setAnchor(anchor);
    myShortenClasspathModeCombo.setAnchor(anchor);
  }
}
