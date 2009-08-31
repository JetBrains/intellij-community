package com.intellij.slicer;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;

/**
 * @author cdr
 */
public class SliceBackwardAction extends CodeInsightAction{
  private final SliceHandler myHandler = new SliceHandler(true);

  protected CodeInsightActionHandler getHandler() {
    return myHandler;
  }
}
