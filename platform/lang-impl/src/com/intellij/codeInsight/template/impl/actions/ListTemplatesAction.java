
package com.intellij.codeInsight.template.impl.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.template.impl.ListTemplatesHandler;

public class ListTemplatesAction extends BaseCodeInsightAction{
  protected CodeInsightActionHandler getHandler() {
    return new ListTemplatesHandler();
  }
}