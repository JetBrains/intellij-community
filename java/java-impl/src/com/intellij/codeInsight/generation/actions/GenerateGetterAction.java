package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.generation.GenerateGetterHandler;

/**
 * Action group which contains Generate... actions
 * Available in the Java code editor context only
 * @author Alexey Kudravtsev
 */ 
public class GenerateGetterAction extends BaseGenerateAction {
  public GenerateGetterAction() {
    super(new GenerateGetterHandler());
  }

}