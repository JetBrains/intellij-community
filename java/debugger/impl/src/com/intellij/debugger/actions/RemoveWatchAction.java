/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.debugger.actions;

import com.intellij.debugger.ui.impl.MainWatchPanel;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;

import java.util.ArrayList;

public class RemoveWatchAction extends DebuggerAction {
  protected DebuggerTreeNodeImpl[] getNodesToDelete(AnActionEvent e) {
    DebuggerTreeNodeImpl[] selectedNodes = getSelectedNodes(e.getDataContext());
    if(selectedNodes == null) return null;
    ArrayList<DebuggerTreeNodeImpl> selectedWatches = new ArrayList<DebuggerTreeNodeImpl>();
    for (int i = 0; i < selectedNodes.length; i++) {
      if(selectedNodes[i].getDescriptor() instanceof WatchItemDescriptor) {
        selectedWatches.add(selectedNodes[i]);
      }
    }

    return selectedWatches.toArray(new DebuggerTreeNodeImpl[selectedWatches.size()]);
  }

  public void actionPerformed(AnActionEvent e) {
    DebuggerTreeNodeImpl [] nodes = getNodesToDelete(e);
    if (nodes == null || nodes.length == 0) return;

    MainWatchPanel watchPanel = (MainWatchPanel)getPanel(e.getDataContext());

    for (int i = 0; i < nodes.length; i++) {
      DebuggerTreeNodeImpl node = nodes[i];
      watchPanel.getWatchTree().removeWatch(node);
    }
  }

  protected void updatePresentation(Presentation presentation, int watchesCount) {
    presentation.setText(DebuggerBundle.message("action.remove.watch.text", watchesCount));
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DebuggerTreeNodeImpl[] nodes = getNodesToDelete(event);
    if (nodes != null && nodes.length > 0) {
      presentation.setEnabled(true);
    }
    else {
      presentation.setEnabled(false);
    }
    updatePresentation(presentation, nodes != null? nodes.length : 0);
  }
}
