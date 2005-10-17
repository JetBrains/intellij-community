package com.intellij.debugger.actions;

import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;

import java.util.Enumeration;

/**
 * User: lex
 * Date: Sep 26, 2003
 * Time: 6:24:44 PM
 */
public class RemoveAllWatchesAction extends RemoveWatchAction {
  protected DebuggerTreeNodeImpl[] getNodesToDelete(AnActionEvent e) {
    DebuggerTree tree = getTree(e.getDataContext());
    if(tree == null) return null;
    DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl)tree.getModel().getRoot();
    DebuggerTreeNodeImpl [] result = new DebuggerTreeNodeImpl[root.getChildCount()];
    int i = 0;
    for(Enumeration enumeration = root.children(); enumeration.hasMoreElements(); i++) {
      DebuggerTreeNodeImpl node = (DebuggerTreeNodeImpl)enumeration.nextElement();
      result[i] = node;
    }
    return result;
  }

  protected void updatePresentation(Presentation presentation, int watchesCount) {
  }
}
