package com.intellij.codeInsight.template;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;

/**
 * @author yole
 */
public interface TemplateLookupSelectionHandler {
  Key<TemplateLookupSelectionHandler> KEY_IN_LOOKUP_ITEM = Key.create("templateLookupSelectionHandler");

  void itemSelected(LookupElement item, final PsiFile psiFile, final Document document, final int segmentStart, final int segmentEnd);
}
