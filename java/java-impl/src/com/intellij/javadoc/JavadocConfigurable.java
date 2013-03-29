/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.javadoc;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiKeyword;

import javax.swing.*;

public final class JavadocConfigurable implements Configurable {
  private JavadocGenerationPanel myPanel;
  private final JavadocConfiguration myConfiguration;

  public JavadocConfigurable(JavadocConfiguration configuration) {
    myConfiguration = configuration;
  }

  @Override
  public JComponent createComponent() {
    myPanel = new JavadocGenerationPanel();
    return myPanel.myPanel;
  }

  public void applyTo(JavadocConfiguration configuration) {
    configuration.OUTPUT_DIRECTORY = StringUtil.isEmptyOrSpaces(myPanel.myTfOutputDir.getText()) ? null : FileUtilRt.toSystemIndependentName(myPanel.myTfOutputDir.getText());
    configuration.OTHER_OPTIONS = StringUtil.nullize(myPanel.myOtherOptionsField.getText(), true);
    configuration.HEAP_SIZE = StringUtil.nullize(myPanel.myHeapSizeField.getText(), true);
    configuration.LOCALE = StringUtil.nullize(myPanel.myLocaleTextField.getText(), true);
    configuration.OPEN_IN_BROWSER = myPanel.myOpenInBrowserCheckBox.isSelected();
    configuration.OPTION_SCOPE = StringUtil.nullize(myPanel.getScope(), true);
    configuration.OPTION_HIERARCHY = myPanel.myHierarchy.isSelected();
    configuration.OPTION_NAVIGATOR = myPanel.myNavigator.isSelected();
    configuration.OPTION_INDEX = myPanel.myIndex.isSelected();
    configuration.OPTION_SEPARATE_INDEX = myPanel.mySeparateIndex.isSelected();
    configuration.OPTION_DOCUMENT_TAG_USE = myPanel.myTagUse.isSelected();
    configuration.OPTION_DOCUMENT_TAG_AUTHOR = myPanel.myTagAuthor.isSelected();
    configuration.OPTION_DOCUMENT_TAG_VERSION = myPanel.myTagVersion.isSelected();
    configuration.OPTION_DOCUMENT_TAG_DEPRECATED = myPanel.myTagDeprecated.isSelected();
    configuration.OPTION_DEPRECATED_LIST = myPanel.myDeprecatedList.isSelected();
    configuration.OPTION_INCLUDE_LIBS = myPanel.myIncludeLibraryCb.isSelected();
  }

  public void loadFrom(JavadocConfiguration configuration) {
    myPanel.myTfOutputDir.setText(
      configuration.OUTPUT_DIRECTORY == null ? "" : FileUtilRt.toSystemDependentName(configuration.OUTPUT_DIRECTORY));
    myPanel.myOtherOptionsField.setText(configuration.OTHER_OPTIONS);
    myPanel.myHeapSizeField.setText(configuration.HEAP_SIZE);
    myPanel.myLocaleTextField.setText(configuration.LOCALE);
    myPanel.myOpenInBrowserCheckBox.setSelected(configuration.OPEN_IN_BROWSER);
    myPanel.setScope(configuration.OPTION_SCOPE);
    myPanel.myHierarchy.setSelected(configuration.OPTION_HIERARCHY);
    myPanel.myNavigator.setSelected(configuration.OPTION_NAVIGATOR);
    myPanel.myIndex.setSelected(configuration.OPTION_INDEX);
    myPanel.mySeparateIndex.setSelected(configuration.OPTION_SEPARATE_INDEX);
    myPanel.myTagUse.setSelected(configuration.OPTION_DOCUMENT_TAG_USE);
    myPanel.myTagAuthor.setSelected(configuration.OPTION_DOCUMENT_TAG_AUTHOR);
    myPanel.myTagVersion.setSelected(configuration.OPTION_DOCUMENT_TAG_VERSION);
    myPanel.myTagDeprecated.setSelected(configuration.OPTION_DOCUMENT_TAG_DEPRECATED);
    myPanel.myDeprecatedList.setSelected(configuration.OPTION_DEPRECATED_LIST);

    myPanel.mySeparateIndex.setEnabled(myPanel.myIndex.isSelected());
    myPanel.myDeprecatedList.setEnabled(myPanel.myTagDeprecated.isSelected());

    myPanel.myIncludeLibraryCb.setSelected(configuration.OPTION_INCLUDE_LIBS);
  }

  @Override
  public boolean isModified() {
    boolean isModified;

    final JavadocConfiguration configuration = myConfiguration;
    isModified = !StringUtil.equals(myPanel.myTfOutputDir.getText(), configuration.OUTPUT_DIRECTORY == null
                                                                     ? ""
                                                                     : FileUtilRt.toSystemDependentName(configuration.OUTPUT_DIRECTORY));
    isModified |= !StringUtil.equals(myPanel.myOtherOptionsField.getText(), configuration.OTHER_OPTIONS);
    isModified |= !StringUtil.equals(myPanel.myHeapSizeField.getText(), configuration.HEAP_SIZE);
    isModified |= myPanel.myOpenInBrowserCheckBox.isSelected() != configuration.OPEN_IN_BROWSER;
    String string2 = (configuration.OPTION_SCOPE == null ? PsiKeyword.PROTECTED : configuration.OPTION_SCOPE);
    isModified |= !StringUtil.equals(myPanel.getScope(), string2);
    isModified |= myPanel.myHierarchy.isSelected() != configuration.OPTION_HIERARCHY;
    isModified |= myPanel.myNavigator.isSelected() != configuration.OPTION_NAVIGATOR;
    isModified |= myPanel.myIndex.isSelected() != configuration.OPTION_INDEX;
    isModified |= myPanel.mySeparateIndex.isSelected() != configuration.OPTION_SEPARATE_INDEX;
    isModified |= myPanel.myTagUse.isSelected() != configuration.OPTION_DOCUMENT_TAG_USE;
    isModified |= myPanel.myTagAuthor.isSelected() != configuration.OPTION_DOCUMENT_TAG_AUTHOR;
    isModified |= myPanel.myTagVersion.isSelected() != configuration.OPTION_DOCUMENT_TAG_VERSION;
    isModified |= myPanel.myTagDeprecated.isSelected() != configuration.OPTION_DOCUMENT_TAG_DEPRECATED;
    isModified |= myPanel.myDeprecatedList.isSelected() != configuration.OPTION_DEPRECATED_LIST;
    isModified |= myPanel.myIncludeLibraryCb.isSelected() != configuration.OPTION_INCLUDE_LIBS;

    return isModified;
  }

  @Override
  public final void apply() {
    applyTo(myConfiguration);
  }

  @Override
  public void reset() {
    loadFrom(myConfiguration);
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
  }

  @Override
  public String getDisplayName() {
    return null;
  }

  @Override
  public String getHelpTopic() {
    return "project.propJavaDoc";
  }

  public String getOutputDir() {
    return myPanel.myTfOutputDir.getText();
  }

  public JTextField getOutputDirField() {
    return myPanel.myTfOutputDir.getTextField();
  }
}
