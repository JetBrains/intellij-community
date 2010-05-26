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
package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

@State(
  name="JavaCodeFoldingSettings",
  storages= {
    @Storage(
      id="other",
      file = "$APP_CONFIG$/editor.codeinsight.xml"
    )}
)
public class JavaCodeFoldingSettingsImpl extends JavaCodeFoldingSettings implements PersistentStateComponent<JavaCodeFoldingSettingsImpl>,
                                                                                    ExportableComponent {
  public boolean isCollapseImports() {
    return CodeFoldingSettings.getInstance().COLLAPSE_IMPORTS;
  }

  public void setCollapseImports(boolean value) {
    CodeFoldingSettings.getInstance().COLLAPSE_IMPORTS = value;
  }

  @Override
  public boolean isCollapseLambdas() {
    return COLLAPSE_CLOSURES;
  }

  public void setCollapseLambdas(boolean value) {
    COLLAPSE_CLOSURES = value;
  }

  public boolean isCollapseConstructorGenericParameters() {
    return COLLAPSE_CONSTRUCTOR_GENERIC_PARAMETERS;
  }

  public void setCollapseConstructorGenericParameters(boolean value) {
    COLLAPSE_CONSTRUCTOR_GENERIC_PARAMETERS = value;
  }

  public boolean isCollapseMethods() {
    return CodeFoldingSettings.getInstance().COLLAPSE_METHODS;
  }

  public void setCollapseMethods(boolean value) {
    CodeFoldingSettings.getInstance().COLLAPSE_METHODS = value;
  }

  public boolean isCollapseAccessors() {
    return COLLAPSE_ACCESSORS;
  }

  public void setCollapseAccessors(boolean value) {
    COLLAPSE_ACCESSORS = value;
  }

  public boolean isCollapseInnerClasses() {
    return COLLAPSE_INNER_CLASSES;
  }

  public void setCollapseInnerClasses(boolean value) {
    COLLAPSE_INNER_CLASSES = value;
  }

  public boolean isCollapseJavadocs() {
    return CodeFoldingSettings.getInstance().COLLAPSE_DOC_COMMENTS;
  }

  public void setCollapseJavadocs(boolean value) {
    CodeFoldingSettings.getInstance().COLLAPSE_DOC_COMMENTS = value;
  }

  public boolean isCollapseFileHeader() {
    return CodeFoldingSettings.getInstance().COLLAPSE_FILE_HEADER;
  }

  public void setCollapseFileHeader(boolean value) {
    CodeFoldingSettings.getInstance().COLLAPSE_FILE_HEADER = value;
  }

  public boolean isCollapseAnonymousClasses() {
    return COLLAPSE_ANONYMOUS_CLASSES;
  }

  public void setCollapseAnonymousClasses(boolean value) {
    COLLAPSE_ANONYMOUS_CLASSES = value;
  }


  public boolean isCollapseAnnotations() {
    return COLLAPSE_ANNOTATIONS;
  }

  public void setCollapseAnnotations(boolean value) {
    COLLAPSE_ANNOTATIONS = value;
  }

  public boolean isCollapseI18nMessages() {
    return COLLAPSE_I18N_MESSAGES;
  }

  public void setCollapseI18nMessages(boolean value) {
    COLLAPSE_I18N_MESSAGES = value;
  }

  @Override
  public boolean isCollapseSuppressWarnings() {
    return COLLAPSE_SUPPRESS_WARNINGS;
  }

  @Override
  public void setCollapseSuppressWarnings(boolean value) {
    COLLAPSE_SUPPRESS_WARNINGS = value;
  }

  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_ACCESSORS = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_INNER_CLASSES = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_ANONYMOUS_CLASSES = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_ANNOTATIONS = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_CLOSURES = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_CONSTRUCTOR_GENERIC_PARAMETERS = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_I18N_MESSAGES = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_SUPPRESS_WARNINGS = true;

  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile("editor.codeinsight")};
  }

  @NotNull
  public String getPresentableName() {
    return IdeBundle.message("code.folding.settings");
  }

  public JavaCodeFoldingSettingsImpl getState() {
    return this;
  }

  public void loadState(final JavaCodeFoldingSettingsImpl state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
