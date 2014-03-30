/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.EditorTextField;
import com.intellij.xdebugger.impl.ui.TextViewer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/*
 * @author Jeka
 */
public class ViewTextAction extends BaseValueAction {
  @Override
  protected void processText(final Project project, final String text, DebuggerTreeNodeImpl node, DebuggerContextImpl debuggerContext) {
    final NodeDescriptorImpl descriptor = node.getDescriptor();
    final String labelText = descriptor instanceof ValueDescriptorImpl? ((ValueDescriptorImpl)descriptor).getValueLabel() : null;
    final MyDialog dialog = new MyDialog(project);
    dialog.setTitle(labelText != null? "View Text for: " + labelText : "View Text");
    dialog.setText(text);
    dialog.show();
  }

  private static class MyDialog extends DialogWrapper {
    private final EditorTextField myTextViewer;

    private MyDialog(Project project) {
      super(project, false);
      setModal(false);
      setCancelButtonText("Close");
      setCrossClosesWindow(true);

      myTextViewer = new TextViewer(project, true, true);
      init();
    }

    public void setText(String text) {
      myTextViewer.setText(text);
    }

    @Override
    @NotNull
    protected Action[] createActions() {
      return new Action[] {getCancelAction()};
    }

    @Override
    protected String getDimensionServiceKey() {
      return "#com.intellij.debugger.actions.ViewTextAction";
    }

    @Override
    protected JComponent createCenterPanel() {
      final JPanel panel = new JPanel(new BorderLayout());
      panel.add(myTextViewer, BorderLayout.CENTER);
      panel.setPreferredSize(new Dimension(300, 200));
      return panel;
    }
  }
}
