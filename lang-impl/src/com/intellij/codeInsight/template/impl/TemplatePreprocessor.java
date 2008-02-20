package com.intellij.codeInsight.template.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;

/**
 * @author yole
 */
public interface TemplatePreprocessor {
  ExtensionPointName<TemplatePreprocessor> EP_NAME = ExtensionPointName.create("com.intellij.liveTemplatePreprocessor");

  void preprocessTemplate(final Editor editor, final PsiFile file, int caretOffset, final String textToInsert, final String templateText);
}
