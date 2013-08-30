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
package com.intellij.ui.treeStructure.filtered;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.treeStructure.PatchedDefaultMutableTreeNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.Comparator;

public class FilteringTreeBuilder extends AbstractTreeBuilder {

  private Object myLastSuccessfulSelect;
  private final Tree myTree;

  private MergingUpdateQueue myRefilterQueue;

  public FilteringTreeBuilder(Tree tree,
                              ElementFilter filter,
                              AbstractTreeStructure structure,
                              @Nullable Comparator<NodeDescriptor> comparator) {
    super(tree,
          (DefaultTreeModel)tree.getModel(),
          structure instanceof FilteringTreeStructure ? structure
                                                      : new FilteringTreeStructure(filter, structure),
          comparator);

    myTree = tree;
    initRootNode();

    if (filter instanceof ElementFilter.Active) {
      ((ElementFilter.Active)filter).addListener(new ElementFilter.Listener() {
        @Override
        public ActionCallback update(final Object preferredSelection, final boolean adjustSelection, final boolean now) {
          return refilter(preferredSelection, adjustSelection, now);
        }
      }, this);
    }

    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        TreePath newPath = e.getNewLeadSelectionPath();
        if (newPath != null) {
          Object element = getElementFor(newPath.getLastPathComponent());
          if (element != null) {
            myLastSuccessfulSelect = element;
          }
        }
      }
    });
  }

  @Override
  public boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    return false;
  }

  @Override
  public boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return true;
  }

  protected final DefaultMutableTreeNode createChildNode(final NodeDescriptor childDescr) {
    return new PatchedDefaultMutableTreeNode(childDescr);
  }

  public void setFilteringMerge(int gap, @Nullable JComponent modalityStateComponent) {
    if (myRefilterQueue != null) {
      Disposer.dispose(myRefilterQueue);
      myRefilterQueue = null;
    }

    if (gap >= 0) {
      JComponent stateComponent = modalityStateComponent;
      if (stateComponent == null) {
        stateComponent = myTree;
      }

      myRefilterQueue = new MergingUpdateQueue("FilteringTreeBuilder", gap, false, stateComponent, this, myTree);
      myRefilterQueue.setRestartTimerOnAdd(true);
    }
  }

  protected boolean isSelectable(Object nodeObject) {
    return true;
  }

  public ActionCallback refilter() {
    return refilter(null, true, false);
  }

  public ActionCallback refilter(@Nullable final Object preferredSelection, final boolean adjustSelection, final boolean now) {
    if (myRefilterQueue != null) {
      myRefilterQueue.cancelAllUpdates();
    }
    final ActionCallback callback = new ActionCallback();
    final Runnable afterCancelUpdate = new Runnable() {
      @Override
      public void run() {
        if (myRefilterQueue == null || now) {
          refilterNow(preferredSelection, adjustSelection).doWhenDone(callback.createSetDoneRunnable());
        }
        else {
          myRefilterQueue.queue(new Update(this) {
            @Override
            public void run() {
              refilterNow(preferredSelection, adjustSelection).notifyWhenDone(callback);
            }

            @Override
            public void setRejected() {
              super.setRejected();
              callback.setDone();
            }
          });
        }
      }
    };
    if (!isDisposed()) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        getUi().cancelUpdate().doWhenProcessed(afterCancelUpdate);
      } else {
        afterCancelUpdate.run();
      }
    }

    return callback;
  }


  protected ActionCallback refilterNow(final Object preferredSelection, final boolean adjustSelection) {
    final ActionCallback selectionDone = new ActionCallback();

    getFilteredStructure().refilter();
    getUi().updateSubtree(getRootNode(), false);
    final Runnable selectionRunnable = new Runnable() {
      @Override
      public void run() {
        revalidateTree();

        Object toSelect = preferredSelection != null ? preferredSelection : myLastSuccessfulSelect;

        if (adjustSelection && toSelect != null) {
          final FilteringTreeStructure.FilteringNode nodeToSelect = getFilteredStructure().getVisibleNodeFor(toSelect);

          if (nodeToSelect != null) {
            select(nodeToSelect, new Runnable() {
              @Override
              public void run() {
                if (getSelectedElements().contains(nodeToSelect)) {
                  myLastSuccessfulSelect = getOriginalNode(nodeToSelect);
                }
                selectionDone.setDone();
              }
            });
          }
          else {
            TreeUtil.ensureSelection(myTree);
            selectionDone.setDone();
          }
        }
        else {
          selectionDone.setDone();
        }
      }
    };
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      queueUpdate().doWhenProcessed(selectionRunnable);
    } else {
      selectionRunnable.run();
    }

    final ActionCallback result = new ActionCallback();

    selectionDone.doWhenDone(new Runnable() {
      @Override
      public void run() {
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          scrollSelectionToVisible(new Runnable() {
            @Override
            public void run() {
              getReady(this).notify(result);
            }
          }, false);
        } else {
          result.setDone();
        }
      }
    }).notifyWhenRejected(result);

    return result;
  }

  public void revalidateTree() {
    revalidateTree(myTree);
  }

  public static void revalidateTree(Tree tree) {
    tree.invalidate();
    tree.setRowHeight(tree.getRowHeight() == -1 ? -2 : -1);
    tree.revalidate();
    tree.repaint();
  }


  private FilteringTreeStructure getFilteredStructure() {
    return ((FilteringTreeStructure)getTreeStructure());
  }

  //todo kirillk
  private boolean isSimpleTree() {
    return myTree instanceof SimpleTree;
  }

  @Nullable
  private Object getSelected() {
    if (isSimpleTree()) {
      FilteringTreeStructure.FilteringNode selected = (FilteringTreeStructure.FilteringNode)((SimpleTree)myTree).getSelectedNode();
      return selected != null ? selected.getDelegate() : null;
    } else {
      final Object[] nodes = myTree.getSelectedNodes(Object.class, null);
      return nodes.length > 0 ? nodes[0] : null;
    }
  }

  public FilteringTreeStructure.FilteringNode getVisibleNodeFor(Object nodeObject) {
    FilteringTreeStructure structure = getFilteredStructure();
    return structure != null ? structure.getVisibleNodeFor(nodeObject) : null;
  }

  public Object getOriginalNode(Object node) {
    return ((FilteringTreeStructure.FilteringNode)node).getDelegate();
  }

  @Override
  protected Object transformElement(Object object) {
    return getOriginalNode(object);
  }

  @Nullable
  public Object getElementFor(Object node) {
    return getUi().getElementFor(node);
  }
}
