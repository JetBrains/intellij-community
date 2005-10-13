package com.intellij.codeInsight.folding.impl.actions;

import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.folding.impl.CollapseBlockHandler;

/**
 * @author ven
 */
public class CollapseBlockAction  extends BaseCodeInsightAction {
  protected CodeInsightActionHandler getHandler() {
    return new CollapseBlockHandler ();
  }
}
