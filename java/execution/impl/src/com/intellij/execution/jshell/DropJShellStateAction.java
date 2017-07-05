/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.jshell;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Eugene Zhuravlev
 * Date: 06-Jun-17
 */
class DropJShellStateAction extends AnAction{
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.jshell.ExecuteJShellAction");
  private static final AnAction ourInstance = new DropJShellStateAction();
  // todo: icon!
  private DropJShellStateAction() {
    super("Drop All Code Snippets", "Invalidate all code snippets in the associated JShell instance", AllIcons.Actions.Delete);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }
    final VirtualFile vFile = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    if (vFile == null) {
      return;
    }

    try {
      final JShellHandler handler = JShellHandler.getAssociatedHandler(vFile);
      if (handler != null) {
        handler.toFront();
        handler.dropState();
      }
    }
    catch (Exception ex) {
      LOG.info(ex);
    }
  }

  public static AnAction getSharedInstance() {
    return ourInstance;
  }
}
