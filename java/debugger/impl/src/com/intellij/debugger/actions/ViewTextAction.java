// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.TextViewer;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.actions.XFetchValueActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/*
 * @author Jeka
 */
public class ViewTextAction extends XFetchValueActionBase {
  @Override
  protected void handle(Project project, String value, XDebuggerTree tree) {}

  @NotNull
  @Override
  protected ValueCollector createCollector(@NotNull AnActionEvent e) {
    XValueNodeImpl node = getStringNode(e);
    return new ValueCollector(XDebuggerTree.getTree(e.getDataContext())) {
      MyDialog dialog = null;

      @Override
      public void handleInCollector(Project project, String value, XDebuggerTree tree) {
        // do not unquote here! Value here must be correct already
        String text = value; //StringUtil.unquoteString(value);
        if (dialog == null) {
          dialog = new MyDialog(project, text, node);
          dialog.setTitle(ActionsBundle.message(node != null ? "action.Debugger.ViewEditText.text" : "action.Debugger.ViewText.text"));
          dialog.show();
        }
        dialog.setText(text);
      }
    };
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    if (getStringNode(e) != null) {
      e.getPresentation().setText(ActionsBundle.message("action.Debugger.ViewEditText.text"));
    }
  }

  private static XValueNodeImpl getStringNode(@NotNull AnActionEvent e) {
    List<XValueNodeImpl> selectedNodes = XDebuggerTreeActionBase.getSelectedNodes(e.getDataContext());
    if (selectedNodes.size() == 1) {
      XValueNodeImpl node = selectedNodes.get(0);
      XValue container = node.getValueContainer();
      if (container instanceof JavaValue && container.getModifier() != null && ((JavaValue)container).getDescriptor().isString()) {
        return node;
      }
    }
    return null;
  }

  //@Override
  //protected void processText(final Project project, final String text, DebuggerTreeNodeImpl node, DebuggerContextImpl debuggerContext) {
  //  final NodeDescriptorImpl descriptor = node.getDescriptor();
  //  final String labelText = descriptor instanceof ValueDescriptorImpl? ((ValueDescriptorImpl)descriptor).getValueLabel() : null;
  //  final MyDialog dialog = new MyDialog(project);
  //  dialog.setTitle(labelText != null? "View Text for: " + labelText : "View Text");
  //  dialog.setText(text);
  //  dialog.show();
  //}

  private static class MyDialog extends DialogWrapper {
    private final TextViewer myTextViewer;
    private final XValueNodeImpl myStringNode;

    private MyDialog(Project project, String initialValue, XValueNodeImpl stringNode) {
      super(project, false);
      myStringNode = stringNode;
      setModal(false);
      setCancelButtonText("Close");
      setOKButtonText("Set");
      getOKAction().setEnabled(false);
      setCrossClosesWindow(true);

      myTextViewer = new TextViewer(initialValue, project, myStringNode == null);
      myTextViewer.addDocumentListener(new DocumentListener() {
        @Override
        public void documentChanged(DocumentEvent e) {
          if (e.getNewLength() + e.getOldLength() > 0) {
            getOKAction().setEnabled(true);
          }
        }
      });
      init();
    }

    public void setText(String text) {
      myTextViewer.setText(text);
    }

    @Override
    protected void doOKAction() {
      if (myStringNode != null) {
        DebuggerUIUtil.setTreeNodeValue(myStringNode,
                                        XExpressionImpl.fromText(
                                          StringUtil.wrapWithDoubleQuote(DebuggerUtils.translateStringValue(myTextViewer.getText()))),
                                        errorMessage -> Messages.showErrorDialog(myStringNode.getTree(), errorMessage));
      }
      super.doOKAction();
    }

    @Override
    @NotNull
    protected Action[] createActions() {
      return myStringNode != null ? new Action[]{getOKAction(), getCancelAction()} : new Action[]{getCancelAction()};
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myTextViewer;
    }

    @Override
    protected String getDimensionServiceKey() {
      return "#com.intellij.debugger.actions.ViewTextAction";
    }

    @Override
    protected JComponent createCenterPanel() {
      BorderLayoutPanel panel = JBUI.Panels.simplePanel(myTextViewer);
      panel.setPreferredSize(JBUI.size(300, 200));
      return panel;
    }
  }
}
