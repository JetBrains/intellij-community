/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class CheckSanityAction extends AnAction {
  public void actionPerformed(final AnActionEvent e) {
    FSRecords.checkSanity();
  }
}