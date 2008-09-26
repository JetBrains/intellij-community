package com.intellij.codeInsight.lookup.impl;

import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.ui.ListScrollingUtil;

public class UpHandler extends LookupActionHandler {
  public UpHandler(EditorActionHandler originalHandler){
    super(originalHandler);
  }

  protected void executeInLookup(final LookupImpl lookup) {
    ListScrollingUtil.moveUp(lookup.getList(), 0);
  }
}
