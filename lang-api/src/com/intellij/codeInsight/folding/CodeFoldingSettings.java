package com.intellij.codeInsight.folding;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author yole
 */
@State(
  name="CodeFoldingSettings",
  storages= {
    @Storage(
      id="other",
      file = "$APP_CONFIG$/editor.codeinsight.xml"
    )}
)
public class CodeFoldingSettings implements PersistentStateComponent<CodeFoldingSettings>, ExportableComponent {
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_IMPORTS = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_METHODS = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_FILE_HEADER = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_DOC_COMMENTS = false;

  public static CodeFoldingSettings getInstance() {
    return ServiceManager.getService(CodeFoldingSettings.class);
  }

  public CodeFoldingSettings getState() {
    return this;
  }

  public void loadState(final CodeFoldingSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[] { PathManager.getOptionsFile("editor.codeinsight") };
  }

  @NotNull
  public String getPresentableName() {
    return IdeBundle.message("code.folding.settings");
  }
}
