package com.intellij.codeInsight.folding.impl.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.folding.impl.CollapseExpandJavadocsHandler;

public class CollapseJavadocsAction extends BaseCodeInsightAction{
  protected CodeInsightActionHandler getHandler(){
    return new CollapseExpandJavadocsHandler(false);
  }
}
