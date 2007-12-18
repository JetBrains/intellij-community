package com.intellij.codeInsight.folding.impl.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.folding.impl.CollapseAllRegionsHandler;

public class CollapseAllRegionsAction extends BaseCodeInsightAction{
  protected CodeInsightActionHandler getHandler(){
    return new CollapseAllRegionsHandler();
  }
}
