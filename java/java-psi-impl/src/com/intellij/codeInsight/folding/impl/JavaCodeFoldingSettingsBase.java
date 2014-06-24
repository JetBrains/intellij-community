package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;

public class JavaCodeFoldingSettingsBase extends JavaCodeFoldingSettings {
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_ACCESSORS = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_ONE_LINE_METHODS = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_INNER_CLASSES = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_ANONYMOUS_CLASSES = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_ANNOTATIONS = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_CLOSURES = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_CONSTRUCTOR_GENERIC_PARAMETERS = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_I18N_MESSAGES = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_SUPPRESS_WARNINGS = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_END_OF_LINE_COMMENTS = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean INLINE_PARAMETER_NAMES_FOR_LITERAL_CALL_ARGUMENTS = false;

  @Override
  public boolean isCollapseImports() {
    return CodeFoldingSettings.getInstance().COLLAPSE_IMPORTS;
  }

  @Override
  public void setCollapseImports(boolean value) {
    CodeFoldingSettings.getInstance().COLLAPSE_IMPORTS = value;
  }

  @Override
  public boolean isCollapseLambdas() {
    return COLLAPSE_CLOSURES;
  }

  @Override
  public void setCollapseLambdas(boolean value) {
    COLLAPSE_CLOSURES = value;
  }

  @Override
  public boolean isCollapseConstructorGenericParameters() {
    return COLLAPSE_CONSTRUCTOR_GENERIC_PARAMETERS;
  }

  @Override
  public void setCollapseConstructorGenericParameters(boolean value) {
    COLLAPSE_CONSTRUCTOR_GENERIC_PARAMETERS = value;
  }

  @Override
  public boolean isCollapseMethods() {
    return CodeFoldingSettings.getInstance().COLLAPSE_METHODS;
  }

  @Override
  public void setCollapseMethods(boolean value) {
    CodeFoldingSettings.getInstance().COLLAPSE_METHODS = value;
  }

  @Override
  public boolean isCollapseAccessors() {
    return COLLAPSE_ACCESSORS;
  }

  @Override
  public void setCollapseAccessors(boolean value) {
    COLLAPSE_ACCESSORS = value;
  }
  @Override
  public boolean isCollapseOneLineMethods() {
    return COLLAPSE_ONE_LINE_METHODS;
  }

  @Override
  public boolean isCollapseInnerClasses() {
    return COLLAPSE_INNER_CLASSES;
  }

  @Override
  public void setCollapseInnerClasses(boolean value) {
    COLLAPSE_INNER_CLASSES = value;
  }

  @Override
  public boolean isCollapseJavadocs() {
    return CodeFoldingSettings.getInstance().COLLAPSE_DOC_COMMENTS;
  }

  @Override
  public void setCollapseJavadocs(boolean value) {
    CodeFoldingSettings.getInstance().COLLAPSE_DOC_COMMENTS = value;
  }

  @Override
  public boolean isCollapseFileHeader() {
    return CodeFoldingSettings.getInstance().COLLAPSE_FILE_HEADER;
  }

  @Override
  public void setCollapseFileHeader(boolean value) {
    CodeFoldingSettings.getInstance().COLLAPSE_FILE_HEADER = value;
  }

  @Override
  public boolean isCollapseAnonymousClasses() {
    return COLLAPSE_ANONYMOUS_CLASSES;
  }

  @Override
  public void setCollapseAnonymousClasses(boolean value) {
    COLLAPSE_ANONYMOUS_CLASSES = value;
  }

  @Override
  public boolean isCollapseAnnotations() {
    return COLLAPSE_ANNOTATIONS;
  }

  @Override
  public void setCollapseAnnotations(boolean value) {
    COLLAPSE_ANNOTATIONS = value;
  }

  @Override
  public boolean isCollapseI18nMessages() {
    return COLLAPSE_I18N_MESSAGES;
  }

  @Override
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

  @Override
  public boolean isCollapseEndOfLineComments() {
    return COLLAPSE_END_OF_LINE_COMMENTS;
  }

  @Override
  public void setCollapseEndOfLineComments(boolean value) {
    COLLAPSE_END_OF_LINE_COMMENTS = value;
  }

  @Override
  public boolean isInlineParameterNamesForLiteralCallArguments() {
    return INLINE_PARAMETER_NAMES_FOR_LITERAL_CALL_ARGUMENTS;
  }

  @Override
  public void setInlineParameterNamesForLiteralCallArguments(boolean value) {
    INLINE_PARAMETER_NAMES_FOR_LITERAL_CALL_ARGUMENTS = value;
  }
}
