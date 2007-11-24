package com.intellij.debugger.ui.content;

import com.intellij.ui.content.ContentUI;

public interface DebuggerContentUIFacade {

  ContentUI getContentUI();

  void restoreLayout();

  boolean isHorizontalToolbar();

  void setHorizontalToolbar(final boolean state);

}
