package com.intellij.debugger.ui.impl;

import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.Enumeration;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class WatchDebuggerTree extends DebuggerTree {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.WatchDebuggerTree");
  private boolean myAllowBreakpoints = false;

  public WatchDebuggerTree(Project project) {
    super(project);
  }

  public DebuggerTreeNodeImpl[] getWatches() {
    DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl)getModel().getRoot();
    DebuggerTreeNodeImpl [] watches = new DebuggerTreeNodeImpl[root.getChildCount()];

    int i = 0;
    for(Enumeration e = root.children(); e.hasMoreElements(); i++) {
      watches[i] = (DebuggerTreeNodeImpl) e.nextElement();
    }

    return watches;
  }

  public int getWatchCount() {
    DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl) getModel().getRoot();
    return root != null ? root.getChildCount() : 0;
  }

  public DebuggerTreeNodeImpl addWatch(WatchItemDescriptor descriptor) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    final DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl) getModel().getRoot();
    WatchItemDescriptor watchDescriptor = new WatchItemDescriptor(getProject(), descriptor.getEvaluationText(), myAllowBreakpoints);
    watchDescriptor.displayAs(descriptor);

    final DebuggerTreeNodeImpl node = DebuggerTreeNodeImpl.createNodeNoUpdate(this, watchDescriptor);
    root.add(node);

    treeChanged();
    getSelectionModel().setSelectionPath(new TreePath(node.getPath()));

    //node.calcValue();

    return node;
  }

  public DebuggerTreeNodeImpl addWatch(TextWithImports text) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    final DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl) getModel().getRoot();
    DebuggerTreeNodeImpl node = DebuggerTreeNodeImpl.createNodeNoUpdate(this, new WatchItemDescriptor(getProject(), text, myAllowBreakpoints));
    root.add(node);

    treeChanged();
    getSelectionModel().setSelectionPath(new TreePath(node.getPath()));

    return node;
  }

  public void removeWatch(DebuggerTreeNodeImpl node) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    LOG.assertTrue(node.getDescriptor() instanceof WatchItemDescriptor);

    DebuggerTreeNodeImpl root          = (DebuggerTreeNodeImpl) getModel().getRoot();
    DebuggerTreeNodeImpl nodeToSelect  = (DebuggerTreeNodeImpl) node.getNextSibling();

    getMutableModel().removeNodeFromParent(node);
    treeChanged();

    if(nodeToSelect == null && root.getChildCount() > 0) {
      nodeToSelect = (DebuggerTreeNodeImpl) root.getChildAt(root.getChildCount() - 1);
    }

    if(nodeToSelect != null) {
      setSelectionPath(new TreePath(nodeToSelect.getPath()));
    }
  }

  protected void build(DebuggerContextImpl context) {
    for (DebuggerTreeNodeImpl node : getWatches()) {
      node.calcValue();
    }
  }

  public static void setWatchNodeText(final DebuggerTreeNodeImpl node, TextWithImports text) {
    ((WatchItemDescriptor)node.getDescriptor()).setEvaluationText(text);
    node.calcValue();
  }

  public void setAllowBreakpoints(boolean b) {
    myAllowBreakpoints = b;
    DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl)getMutableModel().getRoot();
    for(Enumeration enumeration = root.children(); enumeration.hasMoreElements();){
      DebuggerTreeNodeImpl child = (DebuggerTreeNodeImpl)enumeration.nextElement();
      ((WatchItemDescriptor)child.getDescriptor()).setAllowBreakpoints(b);
    }      
  }
}
