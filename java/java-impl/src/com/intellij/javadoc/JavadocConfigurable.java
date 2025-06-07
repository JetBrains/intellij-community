// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javadoc;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.io.File;

public final class JavadocConfigurable implements Configurable {
  private JavadocGenerationAdditionalUi myPanel;
  private final JavadocConfiguration myConfiguration;
  private final Project myProject;

  public JavadocConfigurable(JavadocConfiguration configuration, Project project) {
    myConfiguration = configuration;
    myProject = project;
  }

  public static boolean sdkHasJavadocUrls(Project project) {
    Sdk sdk = JavadocGeneratorRunProfile.getSdk(project);
    return sdk != null && sdk.getRootProvider().getFiles(JavadocOrderRootType.getInstance()).length > 0;
  }

  @Override
  public JComponent createComponent() {
    myPanel = new JavadocGenerationAdditionalUi();
    myPanel.myLinkToJdkDocs.setEnabled(sdkHasJavadocUrls(myProject));
    return myPanel.getPanel();
  }

  public void applyTo(JavadocConfiguration configuration) {
    configuration.OUTPUT_DIRECTORY = toSystemIndependentFormat(myPanel.myTfOutputDir.getText());
    configuration.OTHER_OPTIONS = convertString(myPanel.myOtherOptionsField.getText());
    configuration.HEAP_SIZE = convertString(myPanel.myHeapSizeField.getText());
    configuration.LOCALE = convertString(myPanel.myLocaleTextField.getText());
    configuration.OPEN_IN_BROWSER = myPanel.myOpenInBrowserCheckBox.isSelected();
    configuration.OPTION_SCOPE = myPanel.myScopeCombo.getItem();
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
    configuration.OPTION_LINK_TO_JDK_DOCS = myPanel.myLinkToJdkDocs.isSelected();
  }

  public void loadFrom(JavadocConfiguration configuration) {
    myPanel.myTfOutputDir.setText(toUserSystemFormat(configuration.OUTPUT_DIRECTORY));
    myPanel.myOtherOptionsField.setText(configuration.OTHER_OPTIONS);
    myPanel.myHeapSizeField.setText(configuration.HEAP_SIZE);
    myPanel.myLocaleTextField.setText(configuration.LOCALE);
    myPanel.myOpenInBrowserCheckBox.setSelected(configuration.OPEN_IN_BROWSER);
    myPanel.myScopeCombo.setSelectedItem(configuration.OPTION_SCOPE);
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
    myPanel.myLinkToJdkDocs.setSelected(configuration.OPTION_LINK_TO_JDK_DOCS);
  }

  @Override
  public boolean isModified() {
    boolean isModified;

    final JavadocConfiguration configuration = myConfiguration;
    isModified = !compareStrings(myPanel.myTfOutputDir.getText(), toUserSystemFormat(configuration.OUTPUT_DIRECTORY));
    isModified |= !compareStrings(myPanel.myOtherOptionsField.getText(), configuration.OTHER_OPTIONS);
    isModified |= !compareStrings(myPanel.myHeapSizeField.getText(), configuration.HEAP_SIZE);
    isModified |= myPanel.myOpenInBrowserCheckBox.isSelected() != configuration.OPEN_IN_BROWSER;
    isModified |= !compareStrings(myPanel.myScopeCombo.getItem(), (configuration.OPTION_SCOPE == null ? JavaKeywords.PROTECTED : configuration.OPTION_SCOPE));
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
    isModified |= myPanel.myLinkToJdkDocs.isSelected() != configuration.OPTION_LINK_TO_JDK_DOCS;

    return isModified;
  }

  @Override
  public void apply() {
    applyTo(myConfiguration);
  }

  @Override
  public void reset() {
    loadFrom(myConfiguration);
  }

  private static boolean compareStrings(String string1, String string2) {
    if (string1 == null) {
      string1 = "";
    }
    if (string2 == null) {
      string2 = "";
    }
    return string1.equals(string2);
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
  }

  private static String convertString(String s) {
    return StringUtil.nullize(s, true);
  }

  private static String toSystemIndependentFormat(String directory) {
    if (directory.isEmpty()) {
      return null;
    }
    return directory.replace(File.separatorChar, '/');
  }

  private static @NlsSafe String toUserSystemFormat(String directory) {
    if (directory == null) {
      return "";
    }
    return directory.replace('/', File.separatorChar);
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
