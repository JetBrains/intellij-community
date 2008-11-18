package com.intellij.debugger.actions;

import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.ValueMarkup;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;

/*
 * Class SetValueAction
 * @author Jeka
 */
public class MarkObjectAction extends DebuggerAction {
  private final String MARK_TEXT = ActionsBundle.message("action.Debugger.MarkObject.text");
  private final String UNMARK_TEXT = ActionsBundle.message("action.Debugger.MarkObject.unmark.text");

  public void actionPerformed(final AnActionEvent event) {
    final DebuggerTreeNodeImpl node = getSelectedNode(event.getDataContext());
    if (node == null) {
      return;
    }
    final NodeDescriptorImpl descriptor = node.getDescriptor();
    if (!(descriptor instanceof ValueDescriptorImpl)) {
      return;
    }
    
    final DebuggerTree tree = node.getTree();
    tree.saveState(node);
    
    final ValueDescriptorImpl valueDescriptor = ((ValueDescriptorImpl)descriptor);
    final DebuggerContextImpl debuggerContext = tree.getDebuggerContext();
    final ValueMarkup markup = valueDescriptor.getMarkup(debuggerContext.getDebugProcess());
    try {
      if (markup != null) {
        valueDescriptor.setMarkup(debuggerContext.getDebugProcess(), null);
      }
      else {
        final ValueMarkup valueMarkup = ObjectMarkupPropertiesDialog.chooseMarkup(valueDescriptor.getName());
        if (valueMarkup != null) {
          valueDescriptor.setMarkup(debuggerContext.getDebugProcess(), valueMarkup);
        }
        else {
          return;
        }
      }
    }
    finally {
      tree.restoreState(node);
      node.labelChanged();
    }
    
    final DebuggerSession session = debuggerContext.getDebuggerSession();
    if (session != null) {
      session.refresh(true);
    }
  }

  public void update(AnActionEvent e) {
    boolean enable = false;
    String text = MARK_TEXT;
    final DebuggerTreeNodeImpl node = getSelectedNode(e.getDataContext());
    if (node != null) {
      final NodeDescriptorImpl descriptor = node.getDescriptor();
      enable = (descriptor instanceof ValueDescriptor);
      if (enable) {
        final ValueMarkup markup = ((ValueDescriptor)descriptor).getMarkup(node.getTree().getDebuggerContext().getDebugProcess());
        if (markup != null) { // already exists
          text = UNMARK_TEXT; 
        }
      }
    }
    final Presentation presentation = e.getPresentation();
    presentation.setVisible(enable);
    presentation.setText(text);
  }
}