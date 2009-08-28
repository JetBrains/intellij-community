package com.intellij.application.options.editor;

import com.intellij.openapi.options.BeanConfigurable;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.codeInsight.folding.CodeFoldingSettings;

/**
 * @author yole
 */
public class BaseCodeFoldingOptionsProvider extends BeanConfigurable<CodeFoldingSettings> implements CodeFoldingOptionsProvider {
  protected BaseCodeFoldingOptionsProvider() {
    super(CodeFoldingSettings.getInstance());
    checkBox("COLLAPSE_FILE_HEADER", ApplicationBundle.message("checkbox.collapse.file.header"));
    checkBox("COLLAPSE_IMPORTS", ApplicationBundle.message("checkbox.collapse.title.imports"));
    checkBox("COLLAPSE_DOC_COMMENTS", ApplicationBundle.message("checkbox.collapse.javadoc.comments"));
    checkBox("COLLAPSE_METHODS", ApplicationBundle.message("checkbox.collapse.method.bodies"));
  }
}
