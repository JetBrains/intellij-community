package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.VirtualFileManager;

/**
 * @author yole
*/
public class RefreshFileChooserAction extends AnAction {
  public void actionPerformed(final AnActionEvent e) {
    VirtualFileManager.getInstance().refresh(false);
  }
}