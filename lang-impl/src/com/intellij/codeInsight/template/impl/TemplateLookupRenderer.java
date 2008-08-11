package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.impl.ElementLookupRenderer;
import com.intellij.codeInsight.lookup.impl.LookupElementPresentationEx;
import com.intellij.codeInsight.template.Template;
import com.intellij.openapi.util.Key;

/**
 * @author yole
 */
public class TemplateLookupRenderer implements ElementLookupRenderer<Template> {
  public boolean handlesItem(final Object element) {
    return element instanceof Template;
  }

  public void renderElement(final LookupItem item, final Template element, final LookupElementPresentationEx presentation) {
    presentation.setItemText(element.getKey());
    presentation.setTypeText(getTemplateDescriptionString(element, presentation));
  }

  private static String getTemplateDescriptionString(Template template, final LookupElementPresentationEx presentation) {
    TemplateItemsData data = getTemplateItemsData(presentation);
    final int KEY_LENGTH_LIMIT = 10; // longer keys are not really useful, but make popup ugly
    int max = presentation.getMaxLength() - Math.min(KEY_LENGTH_LIMIT, TemplateSettings.getInstance().getMaxKeyLength());
    max = Math.min(max, data.maxTemplateDescriptionLength + 1);

    StringBuilder buffer = new StringBuilder(max);
    buffer.append(' ');
    buffer.append(template.getDescription());
    if (buffer.length() > max){
      final String ellipsis = "...";
      if (max > ellipsis.length()){
        buffer.setLength(max - ellipsis.length());
        buffer.append(ellipsis);
      }
    }
    else if (!data.hasNonTemplates){
      while(buffer.length() < max){
        buffer.append(' ');
      }
    }
    return buffer.toString();
  }

  private static TemplateItemsData getTemplateItemsData(final LookupElementPresentationEx presentation) {
    TemplateItemsData data = presentation.getUserData(KEY);
    if (data == null) {
      data = new TemplateItemsData();
      presentation.putUserData(KEY, data);
      for (LookupElement item : presentation.getItems()) {
        if (!(item.getObject() instanceof Template)) {
          data.hasNonTemplates = true;
          break;
        }
        else {
          Template template = (Template)item.getObject();
          final String description = template.getDescription();
          if (description != null) {
            data.maxTemplateDescriptionLength = Math.max(data.maxTemplateDescriptionLength, description.length());
          }
        }
      }
    }
    return data;
  }

  private static class TemplateItemsData {
    public int maxTemplateDescriptionLength;
    public boolean hasNonTemplates;
  }

  private static final Key<TemplateItemsData> KEY = Key.create("templateItemsData");

}
