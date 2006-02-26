package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.generation.GenerateConstructorHandler;

/**
 * Action group which contains Generate... actions
 * Available in the Java code editor context only
 * @author Alexey Kudravtsev
 */ 
public class GenerateConstructorAction extends BaseGenerateAction {
  public GenerateConstructorAction() {
    super(new GenerateConstructorHandler());
  }
}