package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.generation.GenerateGetterAndSetterHandler;

/**
 * Action group which contains Generate... actions
 * Available in the Java code editor context only
 * @author Alexey Kudravtsev
 */ 
public class GenerateGetterAndSetterAction extends BaseGenerateAction {
  public GenerateGetterAndSetterAction() {
    super(new GenerateGetterAndSetterHandler());
  }

}