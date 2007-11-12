package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;

/**
   * @author Vladimir Kondratyev
 */
final class ShowHiddensAction extends ToggleAction {
  public boolean isSelected(AnActionEvent e) {
    final FileSystemTreeImpl fileSystemTree = e.getData(FileChooserDialogImpl.FILE_SYSTEM_TREE);
    return fileSystemTree != null && fileSystemTree.areHiddensShown();
  }

  public void setSelected(AnActionEvent e, boolean state) {
    final FileSystemTreeImpl fileSystemTree = e.getData(FileChooserDialogImpl.FILE_SYSTEM_TREE);
    if (fileSystemTree != null) {
      fileSystemTree.showHiddens(state);
    }
  }
}