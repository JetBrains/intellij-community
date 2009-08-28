package com.intellij.codeInsight.template;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.PsiElement;

/**
 * @author yole
 */
public abstract class TemplateBuilderFactory {
  public static TemplateBuilderFactory getInstance() {
    return ServiceManager.getService(TemplateBuilderFactory.class);
  }

  public abstract TemplateBuilder createTemplateBuilder(PsiElement element);
}
