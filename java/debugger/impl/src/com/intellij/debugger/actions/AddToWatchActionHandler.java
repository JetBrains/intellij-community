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

/*
 * Class AddToWatchActionHandler
 * @author Jeka
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.debugger.ui.impl.*;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import org.jetbrains.annotations.NotNull;

public class AddToWatchActionHandler extends DebuggerActionHandler {
  @Override
  public boolean isEnabled(@NotNull Project project, AnActionEvent event) {
    DataContext context = event.getDataContext();
    DebuggerTreeNodeImpl[] selectedNodes = DebuggerAction.getSelectedNodes(context);
    boolean enabled = false;
    if (selectedNodes != null && selectedNodes.length > 0) {
      DebuggerTreePanel panel = DebuggerAction.getPanel(context);
      if (panel instanceof VariablesPanel || panel instanceof WatchPanel
          || (panel == null && DebuggerAction.getTree(context) instanceof InspectDebuggerTree)) {
        enabled = true;
        for (DebuggerTreeNodeImpl node : selectedNodes) {
          NodeDescriptorImpl descriptor = node.getDescriptor();
          if (!(descriptor instanceof ValueDescriptorImpl)) {
            enabled = false;
            break;
          }
        }
      }
    }
    else {
      final Editor editor = event.getData(CommonDataKeys.EDITOR);
      enabled = DebuggerUtilsEx.getEditorText(editor) != null;
    }
    return enabled;
  }

  @Override
  public void perform(@NotNull Project project, AnActionEvent event) {
    final DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(event.getDataContext());

    if(debuggerContext == null) return;

    final DebuggerSession session = debuggerContext.getDebuggerSession();
    if(session == null) {
      return;
    }
    final MainWatchPanel watchPanel = DebuggerPanelsManager.getInstance(debuggerContext.getProject()).getWatchPanel();

    if(watchPanel == null) {
      return;
    }

    final DebuggerTreeNodeImpl[] selectedNodes = DebuggerAction.getSelectedNodes(event.getDataContext());

    if(selectedNodes != null && selectedNodes.length > 0) {
      addFromNodes(debuggerContext, watchPanel, selectedNodes);
    }
    else {
      final Editor editor = event.getData(CommonDataKeys.EDITOR);
      if (editor != null) {
        final TextWithImports editorText = DebuggerUtilsEx.getEditorText(editor);
        if (editorText != null) {
          doAddWatch(watchPanel, editorText, null);
        }
      }
    }
  }

  public static void addFromNodes(final DebuggerContextImpl debuggerContext, final MainWatchPanel watchPanel, final DebuggerTreeNodeImpl[] selectedNodes) {
    debuggerContext.getDebugProcess().getManagerThread().schedule(new AddToWatchesCommand(debuggerContext, selectedNodes, watchPanel));
  }

  public static void doAddWatch(final MainWatchPanel watchPanel, final TextWithImports expression, final NodeDescriptorImpl descriptor) {
    final WatchDebuggerTree watchTree = watchPanel.getWatchTree();
    final DebuggerTreeNodeImpl node = watchTree.addWatch(expression, null);
    if (descriptor != null) {
      node.getDescriptor().displayAs(descriptor);
    }
    node.calcValue();
  }

  private static class AddToWatchesCommand extends DebuggerContextCommandImpl {

    private final DebuggerContextImpl myDebuggerContext;
    private final DebuggerTreeNodeImpl[] mySelectedNodes;
    private final MainWatchPanel myWatchPanel;

    public AddToWatchesCommand(DebuggerContextImpl debuggerContext, DebuggerTreeNodeImpl[] selectedNodes, MainWatchPanel watchPanel) {
      super(debuggerContext);
      myDebuggerContext = debuggerContext;
      mySelectedNodes = selectedNodes;
      myWatchPanel = watchPanel;
    }

    @Override
    public void threadAction() {
      final DebuggerSession session = myDebuggerContext.getDebuggerSession();
      if (session == null) {
        return;
      }
      final Project project = session.getProject();
      for (final DebuggerTreeNodeImpl node : mySelectedNodes) {
        final NodeDescriptorImpl descriptor = node.getDescriptor();
        try {
          final TextWithImports expression = DebuggerTreeNodeExpression.createEvaluationText(node, myDebuggerContext);
          if (expression != null) {
            DebuggerInvocationUtil.swingInvokeLater(project, new Runnable() {
              @Override
              public void run() {
                doAddWatch(myWatchPanel, expression, descriptor);
              }
            });
          }
        }
        catch (final EvaluateException e) {
          DebuggerInvocationUtil.swingInvokeLater(project, new Runnable() {
            @Override
            public void run() {
              Messages.showErrorDialog(project, e.getMessage(), ActionsBundle.actionText(XDebuggerActions.ADD_TO_WATCH));
            }
          });
        }
      }
    }

    @Override
    protected void commandCancelled() {
      DebuggerInvocationUtil.swingInvokeLater(myDebuggerContext.getProject(), new Runnable() {
        @Override
        public void run() {
          for (DebuggerTreeNodeImpl node : mySelectedNodes) {
            final NodeDescriptorImpl descriptor = node.getDescriptor();
            if (descriptor instanceof WatchItemDescriptor) {
              final TextWithImports expression = ((WatchItemDescriptor)descriptor).getEvaluationText();
              if (expression != null) {
                doAddWatch(myWatchPanel, expression, descriptor);
              }
            }
          }
        }
      });
    }

  }
}
