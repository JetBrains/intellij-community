package com.intellij.codeInsight.folding.impl.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.folding.impl.ExpandRegionHandler;

public class ExpandRegionAction extends BaseCodeInsightAction{
  protected CodeInsightActionHandler getHandler(){
    return new ExpandRegionHandler();
  }
}
