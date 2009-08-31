package com.intellij.slicer;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;

/**
 * @author cdr
 */
public class SliceForwardAction extends CodeInsightAction{
  private final SliceHandler myHandler = new SliceHandler(false);

  protected CodeInsightActionHandler getHandler() {
    return myHandler;
  }
}