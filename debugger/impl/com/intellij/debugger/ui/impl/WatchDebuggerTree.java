package com.intellij.debugger.ui.impl;

import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import javax.swing.tree.TreePath;
import java.util.Enumeration;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class WatchDebuggerTree extends DebuggerTree {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.WatchDebuggerTree");

  public WatchDebuggerTree(Project project) {
    super(project);
  }

  public DebuggerTreeNodeImpl[] getWatches() {
    DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl)getModel().getRoot();
    DebuggerTreeNodeImpl[] watches = new DebuggerTreeNodeImpl[root.getChildCount()];

    final Enumeration e = root.children();
    int i = 0;
    while(e.hasMoreElements()) {
      watches[i++] = (DebuggerTreeNodeImpl)e.nextElement();
    }

    return watches;
  }

  public int getWatchCount() {
    DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl) getModel().getRoot();
    return root != null ? root.getChildCount() : 0;
  }

  public DebuggerTreeNodeImpl addWatch(WatchItemDescriptor descriptor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl) getModel().getRoot();
    WatchItemDescriptor watchDescriptor = new WatchItemDescriptor(getProject(), descriptor.getEvaluationText());
    watchDescriptor.displayAs(descriptor);

    final DebuggerTreeNodeImpl node = DebuggerTreeNodeImpl.createNodeNoUpdate(this, watchDescriptor);
    root.add(node);

    treeChanged();
    getSelectionModel().setSelectionPath(new TreePath(node.getPath()));

    //node.calcValue();

    return node;
  }

  public DebuggerTreeNodeImpl addWatch(TextWithImports text) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl) getModel().getRoot();
    DebuggerTreeNodeImpl node = DebuggerTreeNodeImpl.createNodeNoUpdate(this, new WatchItemDescriptor(getProject(), text));
    root.add(node);

    treeChanged();
    getSelectionModel().setSelectionPath(new TreePath(node.getPath()));

    return node;
  }

  public void removeWatch(DebuggerTreeNodeImpl node) {
    ApplicationManager.getApplication().assertIsDispatchThread();
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

}
