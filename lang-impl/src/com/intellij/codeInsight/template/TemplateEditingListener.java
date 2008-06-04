package com.intellij.codeInsight.template;

import com.intellij.codeInsight.template.impl.TemplateState;

/**
 * @author Mike
 */
public interface TemplateEditingListener {
  void beforeTemplateFinished(TemplateState state, Template template);
  void templateFinished(Template template);
  void templateCancelled(Template template);
}
