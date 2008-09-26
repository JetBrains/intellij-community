package com.intellij.codeInsight.lookup.impl;

import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.ui.ListScrollingUtil;

public class PageDownHandler extends LookupActionHandler {
  public PageDownHandler(final EditorActionHandler originalHandler) {
    super(originalHandler);
  }

  protected void executeInLookup(final LookupImpl lookup) {
    ListScrollingUtil.movePageDown(lookup.getList());
  }
}
