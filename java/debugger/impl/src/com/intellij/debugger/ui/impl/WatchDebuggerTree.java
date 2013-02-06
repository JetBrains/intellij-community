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
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.Enumeration;

public class WatchDebuggerTree extends DebuggerTree {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.WatchDebuggerTree");

  public WatchDebuggerTree(Project project) {
    super(project);
    getEmptyText().setText(XDebuggerBundle.message("debugger.no.watches"));
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

  public DebuggerTreeNodeImpl addWatch(TextWithImports text, @Nullable String customName) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl) getModel().getRoot();
    final WatchItemDescriptor descriptor = new WatchItemDescriptor(getProject(), text, customName);
    DebuggerTreeNodeImpl node = DebuggerTreeNodeImpl.createNodeNoUpdate(this, descriptor);
    root.add(node);

    treeChanged();
    final TreePath path = new TreePath(node.getPath());
    getSelectionModel().setSelectionPath(path);
    scrollPathToVisible(path);
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
