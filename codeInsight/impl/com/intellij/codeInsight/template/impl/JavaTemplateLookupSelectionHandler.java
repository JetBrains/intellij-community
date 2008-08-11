package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.TemplateLookupSelectionHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.editor.Document;

/**
 * @author yole
 */
public class JavaTemplateLookupSelectionHandler implements TemplateLookupSelectionHandler {
  public void itemSelected(final LookupElement item,
                           final PsiFile psiFile, final Document document, final int segmentStart, final int segmentEnd) {
    JavaTemplateUtil.updateTypeBindings(item.getObject(), psiFile, document, segmentStart, segmentEnd);
  }
}
