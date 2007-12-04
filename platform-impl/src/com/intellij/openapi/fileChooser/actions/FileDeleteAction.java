package com.intellij.openapi.fileChooser.actions;

import com.intellij.ide.DeleteProvider;
import com.intellij.ide.actions.DeleteAction;
import com.intellij.openapi.actionSystem.DataContext;

public class FileDeleteAction extends DeleteAction {
  public FileDeleteAction() {
    setEnabledInModalContext(true);
  }

  protected DeleteProvider getDeleteProvider(DataContext dataContext) {
    return new VirtualFileDeleteProvider();
  }

}
