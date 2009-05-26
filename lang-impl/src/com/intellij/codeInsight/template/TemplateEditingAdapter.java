package com.intellij.codeInsight.template;

import com.intellij.codeInsight.template.impl.TemplateState;

/**
 * @author ven
 */
public abstract class TemplateEditingAdapter implements TemplateEditingListener {

  public void beforeTemplateFinished(final TemplateState state, final Template template) {
  }

  public void templateFinished(Template template) {
  }

  public void templateCancelled(Template template) {
  }

  public void currentVariableChanged(TemplateState templateState, Template template, int oldIndex, int newIndex) {
  }
}
