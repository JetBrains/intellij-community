package com.intellij.codeInsight.template;

/**
 * @author Mike
 */
public interface TemplateEditingListener {
  void templateFinished(Template template);
  void templateCancelled(Template template);
}
