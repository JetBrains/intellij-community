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

  protected boolean isValidForFile(Project project, Editor editor, PsiFile file) {
    if (!super.isValidForFile(project, editor, file)) return false;
    PsiClass targetClass = getTargetClass(editor, file);
    if (targetClass.isEnum()) { //TODO!: this is a hack to overcome the fact that constructor may be parsed as enum constant
      PsiField[] fields = targetClass.getFields();
      if (fields.length == 0 || !(fields[0] instanceof PsiEnumConstant)) return false;
    }

    return true;
  }
}