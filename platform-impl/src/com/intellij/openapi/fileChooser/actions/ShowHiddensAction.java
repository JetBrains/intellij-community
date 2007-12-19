package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.fileChooser.FileSystemTree;

/**
 * @author Vladimir Kondratyev
 */
public final class ShowHiddensAction extends ToggleAction {
  public boolean isSelected(AnActionEvent e) {
    final FileSystemTree fileSystemTree = e.getData(FileSystemTree.DATA_KEY);
    return fileSystemTree != null && fileSystemTree.areHiddensShown();
  }

  public void setSelected(AnActionEvent e, boolean state) {
    final FileSystemTree fileSystemTree = e.getData(FileSystemTree.DATA_KEY);
    if (fileSystemTree != null) {
      fileSystemTree.showHiddens(state);
    }
  }
}