package com.intellij.codeInsight.lookup.impl;

import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.ui.ListScrollingUtil;

class DownHandler extends LookupActionHandler {

  public DownHandler(EditorActionHandler originalHandler){
    super(originalHandler);
  }

  protected void executeInLookup(final LookupImpl lookup) {
    ListScrollingUtil.moveDown(lookup.getList(), 0);
  }
}
