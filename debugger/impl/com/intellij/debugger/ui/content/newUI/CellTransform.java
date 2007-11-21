package com.intellij.debugger.ui.content.newUI;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.content.Content;

public interface CellTransform {

  interface Restore {
    ActionCallback restoreInGrid();
  }

  interface Facade {
    void minimize(Content content, Restore restore);
  }

}
