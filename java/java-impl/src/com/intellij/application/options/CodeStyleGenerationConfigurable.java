/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.application.options;

import com.intellij.application.options.codeStyle.CommenterForm;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.codeStyle.CodeStyleConfigurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.JavaVisibilityPanel;
import com.intellij.ui.SortedListModel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;

public class CodeStyleGenerationConfigurable implements CodeStyleConfigurable {
  private final JavaVisibilityPanel myJavaVisibilityPanel;
  JPanel myPanel;
  private JTextField myFieldPrefixField;
  private JTextField myStaticFieldPrefixField;
  private JTextField myParameterPrefixField;
  private JTextField myLocalVariablePrefixField;

  private JTextField myFieldSuffixField;
  private JTextField myStaticFieldSuffixField;
  private JTextField myParameterSuffixField;
  private JTextField myLocalVariableSuffixField;

  private JCheckBox myCbPreferLongerNames;

  private final CodeStyleSettings mySettings;
  private JCheckBox myCbGenerateFinalParameters;
  private JCheckBox myCbGenerateFinalLocals;
  private JCheckBox myCbUseExternalAnnotations;
  private JCheckBox myInsertOverrideAnnotationCheckBox;
  private JCheckBox myRepeatSynchronizedCheckBox;
  private JPanel myVisibilityPanel;
  
  @SuppressWarnings("unused") private JPanel myCommenterPanel;
  private JPanel myOverridePanel;
  private JBCheckBox myReplaceInstanceOfCb;
  private JBCheckBox myReplaceCastCb;
  private JBCheckBox myReplaceNullCheckCb;
  private CommenterForm myCommenterForm;
  private SortedListModel<String> myRepeatAnnotationsModel;

  public CodeStyleGenerationConfigurable(CodeStyleSettings settings) {
    mySettings = settings;
    myPanel.setBorder(JBUI.Borders.empty(2, 2, 2, 2));
    myJavaVisibilityPanel = new JavaVisibilityPanel(false, true, RefactoringBundle.message("default.visibility.border.title"));
  }

  public JComponent createComponent() {
    myVisibilityPanel.add(myJavaVisibilityPanel, BorderLayout.CENTER);
    GridBagConstraints gc =
      new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 1, GridBagConstraints.NORTHEAST, GridBagConstraints.BOTH,
                             new JBInsets(0, 0, 0, 0), 0, 0);
    final Condition<PsiClass> isApplicable = aClass -> aClass.isAnnotationType();
    //noinspection Convert2Diamond
    myRepeatAnnotationsModel = new SortedListModel<String>(Comparator.naturalOrder());
    myOverridePanel.add(SpecialAnnotationsUtil.createSpecialAnnotationsListControl("Annotations to Copy", false, isApplicable, myRepeatAnnotationsModel), gc);
    return myPanel;
  }

  public void disposeUIResources() {
  }

  public String getDisplayName() {
    return ApplicationBundle.message("title.code.generation");
  }

  public String getHelpTopic() {
    return "reference.settingsdialog.IDE.globalcodestyle.codegen";
  }

  public void reset(@NotNull CodeStyleSettings settings) {
    myCbPreferLongerNames.setSelected(settings.PREFER_LONGER_NAMES);

    myFieldPrefixField.setText(settings.FIELD_NAME_PREFIX);
    myStaticFieldPrefixField.setText(settings.STATIC_FIELD_NAME_PREFIX);
    myParameterPrefixField.setText(settings.PARAMETER_NAME_PREFIX);
    myLocalVariablePrefixField.setText(settings.LOCAL_VARIABLE_NAME_PREFIX);

    myFieldSuffixField.setText(settings.FIELD_NAME_SUFFIX);
    myStaticFieldSuffixField.setText(settings.STATIC_FIELD_NAME_SUFFIX);
    myParameterSuffixField.setText(settings.PARAMETER_NAME_SUFFIX);
    myLocalVariableSuffixField.setText(settings.LOCAL_VARIABLE_NAME_SUFFIX);

    myCbGenerateFinalLocals.setSelected(settings.GENERATE_FINAL_LOCALS);
    myCbGenerateFinalParameters.setSelected(settings.GENERATE_FINAL_PARAMETERS);

    myCbUseExternalAnnotations.setSelected(settings.USE_EXTERNAL_ANNOTATIONS);
    myInsertOverrideAnnotationCheckBox.setSelected(settings.INSERT_OVERRIDE_ANNOTATION);
    myRepeatSynchronizedCheckBox.setSelected(settings.REPEAT_SYNCHRONIZED);
    myJavaVisibilityPanel.setVisibility(settings.VISIBILITY);

    myReplaceCastCb.setSelected(settings.REPLACE_CAST);
    myReplaceInstanceOfCb.setSelected(settings.REPLACE_INSTANCEOF);
    myReplaceNullCheckCb.setSelected(settings.REPLACE_NULL_CHECK);

    myRepeatAnnotationsModel.clear();
    myRepeatAnnotationsModel.addAll(settings.getRepeatAnnotations());
    myCommenterForm.reset(settings);
  }

  public void reset() {
    reset(mySettings);
  }

  public void apply(@NotNull CodeStyleSettings settings) throws ConfigurationException {
    settings.PREFER_LONGER_NAMES = myCbPreferLongerNames.isSelected();

    settings.FIELD_NAME_PREFIX = setPrefixSuffix(myFieldPrefixField.getText(), true);
    settings.STATIC_FIELD_NAME_PREFIX = setPrefixSuffix(myStaticFieldPrefixField.getText(), true);
    settings.PARAMETER_NAME_PREFIX = setPrefixSuffix(myParameterPrefixField.getText(), true);
    settings.LOCAL_VARIABLE_NAME_PREFIX = setPrefixSuffix(myLocalVariablePrefixField.getText(), true);

    settings.FIELD_NAME_SUFFIX = setPrefixSuffix(myFieldSuffixField.getText(), false);
    settings.STATIC_FIELD_NAME_SUFFIX = setPrefixSuffix(myStaticFieldSuffixField.getText(), false);
    settings.PARAMETER_NAME_SUFFIX = setPrefixSuffix(myParameterSuffixField.getText(), false);
    settings.LOCAL_VARIABLE_NAME_SUFFIX = setPrefixSuffix(myLocalVariableSuffixField.getText(), false);

    settings.GENERATE_FINAL_LOCALS = myCbGenerateFinalLocals.isSelected();
    settings.GENERATE_FINAL_PARAMETERS = myCbGenerateFinalParameters.isSelected();

    settings.USE_EXTERNAL_ANNOTATIONS = myCbUseExternalAnnotations.isSelected();
    settings.INSERT_OVERRIDE_ANNOTATION = myInsertOverrideAnnotationCheckBox.isSelected();
    settings.REPEAT_SYNCHRONIZED = myRepeatSynchronizedCheckBox.isSelected();
    
    settings.VISIBILITY = myJavaVisibilityPanel.getVisibility();

    settings.REPLACE_CAST = myReplaceCastCb.isSelected();
    settings.REPLACE_INSTANCEOF = myReplaceInstanceOfCb.isSelected();
    settings.REPLACE_NULL_CHECK = myReplaceNullCheckCb.isSelected();


    myCommenterForm.apply(settings);
    settings.setRepeatAnnotations(myRepeatAnnotationsModel.getItems());

    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      DaemonCodeAnalyzer.getInstance(project).settingsChanged();
    }
  }

  private static String setPrefixSuffix(String text, boolean prefix) throws ConfigurationException {
    text = text.trim();
    if (text.isEmpty()) return text;
    if (!StringUtil.isJavaIdentifier(text)) {
      throw new ConfigurationException("Not a valid java identifier part in " + (prefix ? "prefix" : "suffix") + " \'" + text + "\'");
    }
    return text;
  }

  public void apply() throws ConfigurationException {
    apply(mySettings);
  }

  public boolean isModified(CodeStyleSettings settings) {
    boolean isModified = isModified(myCbPreferLongerNames, settings.PREFER_LONGER_NAMES);

    isModified |= isModified(myFieldPrefixField, settings.FIELD_NAME_PREFIX);
    isModified |= isModified(myStaticFieldPrefixField, settings.STATIC_FIELD_NAME_PREFIX);
    isModified |= isModified(myParameterPrefixField, settings.PARAMETER_NAME_PREFIX);
    isModified |= isModified(myLocalVariablePrefixField, settings.LOCAL_VARIABLE_NAME_PREFIX);

    isModified |= isModified(myFieldSuffixField, settings.FIELD_NAME_SUFFIX);
    isModified |= isModified(myStaticFieldSuffixField, settings.STATIC_FIELD_NAME_SUFFIX);
    isModified |= isModified(myParameterSuffixField, settings.PARAMETER_NAME_SUFFIX);
    isModified |= isModified(myLocalVariableSuffixField, settings.LOCAL_VARIABLE_NAME_SUFFIX);

    isModified |= isModified(myCbGenerateFinalLocals, settings.GENERATE_FINAL_LOCALS);
    isModified |= isModified(myCbGenerateFinalParameters, settings.GENERATE_FINAL_PARAMETERS);

    isModified |= isModified(myCbUseExternalAnnotations, settings.USE_EXTERNAL_ANNOTATIONS);
    isModified |= isModified(myInsertOverrideAnnotationCheckBox, settings.INSERT_OVERRIDE_ANNOTATION);
    isModified |= isModified(myRepeatSynchronizedCheckBox, settings.REPEAT_SYNCHRONIZED);

    isModified |= isModified(myReplaceCastCb, settings.REPLACE_CAST);
    isModified |= isModified(myReplaceInstanceOfCb, settings.REPLACE_INSTANCEOF);
    isModified |= isModified(myReplaceNullCheckCb, settings.REPLACE_NULL_CHECK);

    isModified |= !settings.VISIBILITY.equals(myJavaVisibilityPanel.getVisibility());
    
    isModified |= myCommenterForm.isModified(settings);

    isModified |= !myRepeatAnnotationsModel.getItems().equals(settings.getRepeatAnnotations());

    return isModified;
  }

  public boolean isModified() {
    return isModified(mySettings);
  }

  private void createUIComponents() {
    myCommenterForm =  new CommenterForm(JavaLanguage.INSTANCE);
    myCommenterPanel = myCommenterForm.getCommenterPanel();
  }
}
