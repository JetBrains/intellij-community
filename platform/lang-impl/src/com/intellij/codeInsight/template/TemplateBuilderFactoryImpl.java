package com.intellij.codeInsight.template;

import com.intellij.psi.PsiElement;

/**
 * @author yole
 */
public class TemplateBuilderFactoryImpl extends TemplateBuilderFactory {
  @Override
  public TemplateBuilder createTemplateBuilder(PsiElement element) {
    return new TemplateBuilderImpl(element);
  }
}
