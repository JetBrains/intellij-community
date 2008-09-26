package com.intellij.codeInsight.lookup.impl;

import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.ui.ListScrollingUtil;

public class PageUpHandler extends LookupActionHandler {
  public PageUpHandler(EditorActionHandler originalHandler){
    super(originalHandler);
  }

  protected void executeInLookup(final LookupImpl lookup) {
    ListScrollingUtil.movePageUp(lookup.getList());
  }
}
