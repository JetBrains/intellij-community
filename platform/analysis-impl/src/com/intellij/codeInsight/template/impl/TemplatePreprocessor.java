// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.template.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;

/**
 * When an template is started, allows to prepare the editor before actual template expanding.
 * 
 * For example, for some XML-based languages it make sense to check whether text of template contains 
 * unescaped character and insert CDATA-element to the editor before template expanding.
 *
 * @see TemplateOptionalProcessor
 * @see com.intellij.codeInsight.template.TemplateSubstitutor 
 */
public interface TemplatePreprocessor {
  ExtensionPointName<TemplatePreprocessor> EP_NAME = ExtensionPointName.create("com.intellij.liveTemplatePreprocessor");

  void preprocessTemplate(final Editor editor, final PsiFile file, int caretOffset, final String textToInsert, final String templateText);
}
