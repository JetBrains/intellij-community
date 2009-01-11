package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;

/**
 * @author yole
 */
public class InvalidateCachesAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    FSRecords.invalidateCaches();
    Messages.showMessageDialog(e.getData(PlatformDataKeys.PROJECT),
                               "The caches have been invalidated and will be rebuilt on the next startup",
                               "Invalidate Caches", Messages.getInformationIcon());
  }
}
