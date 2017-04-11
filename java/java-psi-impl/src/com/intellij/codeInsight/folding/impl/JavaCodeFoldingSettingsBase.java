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
package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.util.BooleanTrackableProperty;

public class JavaCodeFoldingSettingsBase extends JavaCodeFoldingSettings {
  private BooleanTrackableProperty COLLAPSE_ACCESSORS = new BooleanTrackableProperty();
  private BooleanTrackableProperty COLLAPSE_ONE_LINE_METHODS  = new BooleanTrackableProperty(true);
  private BooleanTrackableProperty COLLAPSE_INNER_CLASSES = new BooleanTrackableProperty();
  private BooleanTrackableProperty COLLAPSE_ANONYMOUS_CLASSES = new BooleanTrackableProperty();
  private BooleanTrackableProperty COLLAPSE_ANNOTATIONS = new BooleanTrackableProperty();
  private BooleanTrackableProperty COLLAPSE_CLOSURES  = new BooleanTrackableProperty(true);
  private BooleanTrackableProperty COLLAPSE_CONSTRUCTOR_GENERIC_PARAMETERS  = new BooleanTrackableProperty(true);
  private BooleanTrackableProperty COLLAPSE_I18N_MESSAGES  = new BooleanTrackableProperty(true);
  private BooleanTrackableProperty COLLAPSE_SUPPRESS_WARNINGS  = new BooleanTrackableProperty(true);
  private BooleanTrackableProperty COLLAPSE_END_OF_LINE_COMMENTS = new BooleanTrackableProperty();

  @Override
  public boolean isCollapseImports() {
    return CodeFoldingSettings.getInstance().isCollapseImports();
  }

  @Override
  public void setCollapseImports(boolean value) {
    CodeFoldingSettings.getInstance().setCollapseImports(value);
  }

  @Override
  public BooleanTrackableProperty getCollapseImportsProperty() {
    return CodeFoldingSettings.getInstance().getCollapseImportsProperty();
  }

  @Override
  public boolean isCollapseLambdas() {
    return COLLAPSE_CLOSURES.getValue();
  }

  @Override
  public void setCollapseLambdas(boolean value) {
    COLLAPSE_CLOSURES.setValue(value);
  }

  @Override
  public BooleanTrackableProperty getCollapseLambdasProperty() {
    return COLLAPSE_CLOSURES;
  }

  @Override
  public boolean isCollapseConstructorGenericParameters() {
    return COLLAPSE_CONSTRUCTOR_GENERIC_PARAMETERS.getValue();
  }

  @Override
  public void setCollapseConstructorGenericParameters(boolean value) {
    COLLAPSE_CONSTRUCTOR_GENERIC_PARAMETERS.setValue(value);
  }

  @Override
  public BooleanTrackableProperty getCollapseConstructorGenericParametersProperty() {
    return COLLAPSE_CONSTRUCTOR_GENERIC_PARAMETERS;
  }

  @Override
  public boolean isCollapseMethods() {
    return CodeFoldingSettings.getInstance().isCollapseMethods();
  }

  @Override
  public void setCollapseMethods(boolean value) {
    CodeFoldingSettings.getInstance().setCollapseMethods(value);
  }

  @Override
  public BooleanTrackableProperty getCollapseMethodsProperty() {
    return CodeFoldingSettings.getInstance().getCollapseMethodsProperty();
  }

  @Override
  public boolean isCollapseAccessors() {
    return COLLAPSE_ACCESSORS.getValue();
  }

  @Override
  public void setCollapseAccessors(boolean value) {
    COLLAPSE_ACCESSORS.setValue(value);
  }

  @Override
  public BooleanTrackableProperty getCollapseAccessorsProperty() {
    return COLLAPSE_ACCESSORS;
  }

  @Override
  public boolean isCollapseOneLineMethods() {
    return COLLAPSE_ONE_LINE_METHODS.getValue();
  }

  @Override
  public void setCollapseOneLineMethods(boolean value) {
    COLLAPSE_ONE_LINE_METHODS.setValue(value);
  }

  @Override
  public BooleanTrackableProperty getCollapseOneLineMethodsProperty() {
    return COLLAPSE_ONE_LINE_METHODS;
  }

  @Override
  public boolean isCollapseInnerClasses() {
    return COLLAPSE_INNER_CLASSES.getValue();
  }

  @Override
  public void setCollapseInnerClasses(boolean value) {
    COLLAPSE_INNER_CLASSES.setValue(value);
  }

  @Override
  public BooleanTrackableProperty getCollapseInnerClassesProperty() {
    return COLLAPSE_INNER_CLASSES;
  }

  @Override
  public boolean isCollapseJavadocs() {
    return CodeFoldingSettings.getInstance().isCollapseDocComments();
  }

  @Override
  public void setCollapseJavadocs(boolean value) {
    CodeFoldingSettings.getInstance().setCollapseDocComments(value);
  }

  @Override
  public BooleanTrackableProperty getCollapseJavadocsProperty() {
    return CodeFoldingSettings.getInstance().getCollapseDocCommentsProperty();
  }

  @Override
  public boolean isCollapseFileHeader() {
    return CodeFoldingSettings.getInstance().isCollapseFileHeader();
  }

  @Override
  public void setCollapseFileHeader(boolean value) {
    CodeFoldingSettings.getInstance().setCollapseFileHeader(value);
  }

  @Override
  public BooleanTrackableProperty getCollapseFileHeaderProperty() {
    return CodeFoldingSettings.getInstance().getCollapseFileHeaderProperty();
  }

  @Override
  public boolean isCollapseAnonymousClasses() {
    return COLLAPSE_ANONYMOUS_CLASSES.getValue();
  }

  @Override
  public void setCollapseAnonymousClasses(boolean value) {
    COLLAPSE_ANONYMOUS_CLASSES.setValue(value);
  }

  @Override
  public BooleanTrackableProperty getCollapseAnonymousClassesProperty() {
    return COLLAPSE_ANONYMOUS_CLASSES;
  }

  @Override
  public boolean isCollapseAnnotations() {
    return COLLAPSE_ANNOTATIONS.getValue();
  }

  @Override
  public void setCollapseAnnotations(boolean value) {
    COLLAPSE_ANNOTATIONS.setValue(value);
  }

  @Override
  public BooleanTrackableProperty getCollapseAnnotationsProperty() {
    return COLLAPSE_ANNOTATIONS;
  }

  @Override
  public boolean isCollapseI18nMessages() {
    return COLLAPSE_I18N_MESSAGES.getValue();
  }

  @Override
  public void setCollapseI18nMessages(boolean value) {
    COLLAPSE_I18N_MESSAGES.setValue(value);
  }

  @Override
  public BooleanTrackableProperty getCollapseI18nMessagesProperty() {
    return COLLAPSE_I18N_MESSAGES;
  }

  @Override
  public boolean isCollapseSuppressWarnings() {
    return COLLAPSE_SUPPRESS_WARNINGS.getValue();
  }

  @Override
  public void setCollapseSuppressWarnings(boolean value) {
    COLLAPSE_SUPPRESS_WARNINGS.setValue(value);
  }

  @Override
  public BooleanTrackableProperty getCollapseSuppressWarningsProperty() {
    return COLLAPSE_SUPPRESS_WARNINGS;
  }

  @Override
  public boolean isCollapseEndOfLineComments() {
    return COLLAPSE_END_OF_LINE_COMMENTS.getValue();
  }

  @Override
  public void setCollapseEndOfLineComments(boolean value) {
    COLLAPSE_END_OF_LINE_COMMENTS.setValue(value);
  }

  @Override
  public BooleanTrackableProperty getCollapseEndOfLineCommentsProperty() {
    return COLLAPSE_END_OF_LINE_COMMENTS;
  }
}
