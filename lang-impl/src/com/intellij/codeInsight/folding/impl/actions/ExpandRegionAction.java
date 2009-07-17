package com.intellij.codeInsight.folding.impl.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.folding.impl.ExpandRegionHandler;
import com.intellij.openapi.project.DumbAware;

public class ExpandRegionAction extends BaseCodeInsightAction implements DumbAware {
  protected CodeInsightActionHandler getHandler(){
    return new ExpandRegionHandler();
  }
}
