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
  name="CodeFoldingSettings",
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

  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_ACCESSORS = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_INNER_CLASSES = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_ANONYMOUS_CLASSES = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_ANNOTATIONS = false;

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
