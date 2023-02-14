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
import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.codeStyle.CodeStyleConfigurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.refactoring.ui.JavaVisibilityPanel;
import com.intellij.ui.SortedListModel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;
import java.util.function.Predicate;

import static com.intellij.openapi.options.Configurable.isCheckboxModified;
import static com.intellij.openapi.options.Configurable.isFieldModified;

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
  private JBCheckBox myReplaceNullCheckCb;
  private JTextField myTestClassPrefix;
  private JTextField myTestClassSuffix;
  private JTextField mySubclassPrefix;
  private JTextField mySubclassSuffix;
  private JBCheckBox myReplaceSumCb;
  private CommenterForm myCommenterForm;
  private SortedListModel<String> myRepeatAnnotationsModel;

  public CodeStyleGenerationConfigurable(CodeStyleSettings settings) {
    mySettings = settings;
    myPanel.setBorder(JBUI.Borders.empty(2));
    myJavaVisibilityPanel = new JavaVisibilityPanel(false, true, JavaRefactoringBundle.message("default.visibility.border.title"));
  }

  @Override
  public JComponent createComponent() {
    myVisibilityPanel.add(myJavaVisibilityPanel, BorderLayout.CENTER);
    GridBagConstraints gc =
      new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 1, GridBagConstraints.NORTHEAST, GridBagConstraints.BOTH,
                             new JBInsets(0, 0, 0, 0), 0, 0);
    Predicate<PsiClass> isApplicable = PsiClass::isAnnotationType;
    //noinspection Convert2Diamond
    myRepeatAnnotationsModel = new SortedListModel<String>(Comparator.naturalOrder());
    myOverridePanel.add(SpecialAnnotationsUtil.createSpecialAnnotationsListControl(JavaBundle.message("separator.annotations.to.copy"), 
                                                                                   false, myRepeatAnnotationsModel, isApplicable), gc);
    return myPanel;
  }

  @Override
  public String getDisplayName() {
    return ApplicationBundle.message("title.code.generation");
  }

  @Override
  public String getHelpTopic() {
    return "reference.settingsdialog.IDE.globalcodestyle.codegen";
  }

  @Override
  public void reset(@NotNull CodeStyleSettings settings) {
    JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    myCbPreferLongerNames.setSelected(javaSettings.PREFER_LONGER_NAMES);

    myFieldPrefixField.setText(javaSettings.FIELD_NAME_PREFIX);
    myStaticFieldPrefixField.setText(javaSettings.STATIC_FIELD_NAME_PREFIX);
    myParameterPrefixField.setText(javaSettings.PARAMETER_NAME_PREFIX);
    myLocalVariablePrefixField.setText(javaSettings.LOCAL_VARIABLE_NAME_PREFIX);
    mySubclassPrefix.setText(javaSettings.SUBCLASS_NAME_PREFIX);
    myTestClassPrefix.setText(javaSettings.TEST_NAME_PREFIX);

    myFieldSuffixField.setText(javaSettings.FIELD_NAME_SUFFIX);
    myStaticFieldSuffixField.setText(javaSettings.STATIC_FIELD_NAME_SUFFIX);
    myParameterSuffixField.setText(javaSettings.PARAMETER_NAME_SUFFIX);
    myLocalVariableSuffixField.setText(javaSettings.LOCAL_VARIABLE_NAME_SUFFIX);
    mySubclassSuffix.setText(javaSettings.SUBCLASS_NAME_SUFFIX);
    myTestClassSuffix.setText(javaSettings.TEST_NAME_SUFFIX);

    myCbGenerateFinalLocals.setSelected(javaSettings.GENERATE_FINAL_LOCALS);
    myCbGenerateFinalParameters.setSelected(javaSettings.GENERATE_FINAL_PARAMETERS);

    myCbUseExternalAnnotations.setSelected(javaSettings.USE_EXTERNAL_ANNOTATIONS);
    myInsertOverrideAnnotationCheckBox.setSelected(javaSettings.INSERT_OVERRIDE_ANNOTATION);
    myRepeatSynchronizedCheckBox.setSelected(javaSettings.REPEAT_SYNCHRONIZED);
    myJavaVisibilityPanel.setVisibility(javaSettings.VISIBILITY);

    myReplaceInstanceOfCb.setSelected(javaSettings.REPLACE_INSTANCEOF_AND_CAST);
    myReplaceNullCheckCb.setSelected(javaSettings.REPLACE_NULL_CHECK);
    myReplaceSumCb.setSelected(javaSettings.REPLACE_SUM);

    myRepeatAnnotationsModel.clear();
    myRepeatAnnotationsModel.addAll(javaSettings.getRepeatAnnotations());
    myCommenterForm.reset(settings);
  }

  @Override
  public void reset() {
    reset(mySettings);
  }

  @Override
  public void apply(@NotNull CodeStyleSettings settings) throws ConfigurationException {
    JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    javaSettings.PREFER_LONGER_NAMES = myCbPreferLongerNames.isSelected();

    javaSettings.FIELD_NAME_PREFIX = setPrefixSuffix(myFieldPrefixField.getText(), true);
    javaSettings.STATIC_FIELD_NAME_PREFIX = setPrefixSuffix(myStaticFieldPrefixField.getText(), true);
    javaSettings.PARAMETER_NAME_PREFIX = setPrefixSuffix(myParameterPrefixField.getText(), true);
    javaSettings.LOCAL_VARIABLE_NAME_PREFIX = setPrefixSuffix(myLocalVariablePrefixField.getText(), true);
    javaSettings.SUBCLASS_NAME_PREFIX = setPrefixSuffix(mySubclassPrefix.getText(), true);
    javaSettings.TEST_NAME_PREFIX = setPrefixSuffix(myTestClassPrefix.getText(), true);

    javaSettings.FIELD_NAME_SUFFIX = setPrefixSuffix(myFieldSuffixField.getText(), false);
    javaSettings.STATIC_FIELD_NAME_SUFFIX = setPrefixSuffix(myStaticFieldSuffixField.getText(), false);
    javaSettings.PARAMETER_NAME_SUFFIX = setPrefixSuffix(myParameterSuffixField.getText(), false);
    javaSettings.LOCAL_VARIABLE_NAME_SUFFIX = setPrefixSuffix(myLocalVariableSuffixField.getText(), false);
    javaSettings.SUBCLASS_NAME_SUFFIX = setPrefixSuffix(mySubclassSuffix.getText(), false);
    javaSettings.TEST_NAME_SUFFIX = setPrefixSuffix(myTestClassSuffix.getText(), false);

    javaSettings.GENERATE_FINAL_LOCALS = myCbGenerateFinalLocals.isSelected();
    javaSettings.GENERATE_FINAL_PARAMETERS = myCbGenerateFinalParameters.isSelected();

    javaSettings.USE_EXTERNAL_ANNOTATIONS = myCbUseExternalAnnotations.isSelected();
    javaSettings.INSERT_OVERRIDE_ANNOTATION = myInsertOverrideAnnotationCheckBox.isSelected();
    javaSettings.REPEAT_SYNCHRONIZED = myRepeatSynchronizedCheckBox.isSelected();

    javaSettings.VISIBILITY = myJavaVisibilityPanel.getVisibility();

    javaSettings.REPLACE_INSTANCEOF_AND_CAST = myReplaceInstanceOfCb.isSelected();
    javaSettings.REPLACE_NULL_CHECK = myReplaceNullCheckCb.isSelected();
    javaSettings.REPLACE_SUM = myReplaceSumCb.isSelected();


    myCommenterForm.apply(settings);
    javaSettings.setRepeatAnnotations(myRepeatAnnotationsModel.getItems());

    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      DaemonCodeAnalyzer.getInstance(project).settingsChanged();
    }
  }

  private static String setPrefixSuffix(String text, boolean prefix) throws ConfigurationException {
    text = text.trim();
    if (text.isEmpty()) return text;
    if (!StringUtil.isJavaIdentifier(text)) {
      final @Nls String message = JavaBundle.message(prefix
                                                     ? "code.style.generation.settings.error.not.valid.identifier.part.in.prefix"
                                                     : "code.style.generation.settings.error.not.valid.identifier.part.in.suffix", text);
      throw new ConfigurationException(message);
    }
    return text;
  }

  @Override
  public void apply() throws ConfigurationException {
    apply(mySettings);
  }

  public boolean isModified(CodeStyleSettings settings) {
    JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    boolean isModified = isCheckboxModified(myCbPreferLongerNames, javaSettings.PREFER_LONGER_NAMES);

    isModified |= isFieldModified(myFieldPrefixField, javaSettings.FIELD_NAME_PREFIX);
    isModified |= isFieldModified(myStaticFieldPrefixField, javaSettings.STATIC_FIELD_NAME_PREFIX);
    isModified |= isFieldModified(myParameterPrefixField, javaSettings.PARAMETER_NAME_PREFIX);
    isModified |= isFieldModified(myLocalVariablePrefixField, javaSettings.LOCAL_VARIABLE_NAME_PREFIX);
    isModified |= isFieldModified(mySubclassPrefix, javaSettings.SUBCLASS_NAME_PREFIX);
    isModified |= isFieldModified(myTestClassPrefix, javaSettings.TEST_NAME_PREFIX);

    isModified |= isFieldModified(myFieldSuffixField, javaSettings.FIELD_NAME_SUFFIX);
    isModified |= isFieldModified(myStaticFieldSuffixField, javaSettings.STATIC_FIELD_NAME_SUFFIX);
    isModified |= isFieldModified(myParameterSuffixField, javaSettings.PARAMETER_NAME_SUFFIX);
    isModified |= isFieldModified(myLocalVariableSuffixField, javaSettings.LOCAL_VARIABLE_NAME_SUFFIX);
    isModified |= isFieldModified(mySubclassSuffix, javaSettings.SUBCLASS_NAME_SUFFIX);
    isModified |= isFieldModified(myTestClassSuffix, javaSettings.TEST_NAME_SUFFIX);

    isModified |= isCheckboxModified(myCbGenerateFinalLocals, javaSettings.GENERATE_FINAL_LOCALS);
    isModified |= isCheckboxModified(myCbGenerateFinalParameters, javaSettings.GENERATE_FINAL_PARAMETERS);

    isModified |= isCheckboxModified(myCbUseExternalAnnotations, javaSettings.USE_EXTERNAL_ANNOTATIONS);
    isModified |= isCheckboxModified(myInsertOverrideAnnotationCheckBox, javaSettings.INSERT_OVERRIDE_ANNOTATION);
    isModified |= isCheckboxModified(myRepeatSynchronizedCheckBox, javaSettings.REPEAT_SYNCHRONIZED);

    isModified |= isCheckboxModified(myReplaceInstanceOfCb, javaSettings.REPLACE_INSTANCEOF_AND_CAST);
    isModified |= isCheckboxModified(myReplaceNullCheckCb, javaSettings.REPLACE_NULL_CHECK);
    isModified |= isCheckboxModified(myReplaceSumCb, javaSettings.REPLACE_SUM);

    isModified |= !javaSettings.VISIBILITY.equals(myJavaVisibilityPanel.getVisibility());

    isModified |= myCommenterForm.isModified(settings);

    isModified |= !myRepeatAnnotationsModel.getItems().equals(javaSettings.getRepeatAnnotations());

    return isModified;
  }

  @Override
  public boolean isModified() {
    return isModified(mySettings);
  }

  private void createUIComponents() {
    myCommenterForm =  new CommenterForm(JavaLanguage.INSTANCE);
    myCommenterPanel = myCommenterForm.getCommenterPanel();
  }
}
