// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.TemplateLookupSelectionHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.editor.Document;


public class JavaTemplateLookupSelectionHandler implements TemplateLookupSelectionHandler {
  @Override
  public void itemSelected(final LookupElement item,
                           final PsiFile psiFile, final Document document, final int segmentStart, final int segmentEnd) {
    JavaTemplateUtil.updateTypeBindings(item.getObject(), psiFile, document, segmentStart, segmentEnd);
  }
}
