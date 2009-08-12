package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.impl.ElementLookupRenderer;
import com.intellij.codeInsight.template.Template;

/**
 * @author yole
 */
public class TemplateLookupRenderer implements ElementLookupRenderer<Template> {
  public boolean handlesItem(final Object element) {
    return element instanceof Template;
  }

  public void renderElement(final LookupItem item, final Template element, final LookupElementPresentation presentation) {
    presentation.setItemText(element.getKey());
    presentation.setTypeText(element.getDescription());
  }

}
