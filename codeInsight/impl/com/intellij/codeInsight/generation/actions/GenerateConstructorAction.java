package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.generation.GenerateConstructorHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;

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