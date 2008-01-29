package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.generation.GenerateConstructorHandler;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;

/**
 * Action group which contains Generate... actions
 * Available in the Java code editor context only
 * @author Alexey Kudravtsev
 */ 
public class GenerateConstructorAction extends BaseGenerateAction {
  public GenerateConstructorAction() {
    super(new GenerateConstructorHandler());
  }

  protected boolean isValidForClass(final PsiClass targetClass) {
    return super.isValidForClass(targetClass) && !(targetClass instanceof PsiAnonymousClass);
  }
}