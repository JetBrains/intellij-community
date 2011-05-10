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
import com.intellij.openapi.project.Project;
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

  public FilteringTreeBuilder(Project project,
                              Tree tree,
                              ElementFilter filter,
                              AbstractTreeStructure structure,
                              Comparator<NodeDescriptor> comparator) {
    super(tree, (DefaultTreeModel)tree.getModel(), new FilteringTreeStructure(project, filter, structure), comparator);
    myTree = tree;
    initRootNode();

    if (filter instanceof ElementFilter.Active) {
      ((ElementFilter.Active)filter).addListener(new ElementFilter.Listener() {
        public ActionCallback update(final Object preferredSelection, final boolean adjustSelection, final boolean now) {
          return refilter(preferredSelection, adjustSelection, now);
        }
      }, this);
    }

    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
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

  public boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    return false;
  }

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

  public void refilter() {
    refilter(null, true, false);
  }

  public ActionCallback refilter(final Object preferredSelection, final boolean adjustSelection, final boolean now) {
    if (myRefilterQueue == null || now) {
      return refilterNow(preferredSelection, adjustSelection);
    }
    else {
      final ActionCallback result = new ActionCallback();
      myRefilterQueue.queue(new Update(this) {
        public void run() {
          refilterNow(preferredSelection, adjustSelection).notifyWhenDone(result);
        }

        @Override
        public void setRejected() {
          super.setRejected();
          result.setDone();
        }
      });

      return result;
    }
  }

  protected ActionCallback refilterNow(final Object preferredSelection, final boolean adjustSelection) {
    final ActionCallback selectionDone = new ActionCallback();

    getFilteredStructure().refilter();
    updateFromRoot();

    getReady(this).doWhenDone(new Runnable() {
      public void run() {
        Object toSelect = preferredSelection != null ? preferredSelection : myLastSuccessfulSelect;

        if (adjustSelection && toSelect != null) {
          final FilteringTreeStructure.Node nodeToSelect = getFilteredStructure().getVisibleNodeFor(toSelect);

          if (nodeToSelect != null) {
            select(nodeToSelect, new Runnable() {
              public void run() {
                if (getSelectedElements().contains(nodeToSelect)) {
                  myLastSuccessfulSelect = getOriginalNode(nodeToSelect);
                }
                selectionDone.setDone();
              }
            });
          } else {
            TreeUtil.ensureSelection(myTree);
            selectionDone.setDone();
          }
        } else {
          selectionDone.setDone();
        }
      }
    });

    final ActionCallback result = new ActionCallback();

    selectionDone.doWhenDone(new Runnable() {
      public void run() {
        scrollSelectionToVisible(new Runnable() {
          public void run() {
            getReady(this).notify(result);
          }
        }, false);
      }
    });

    return result;
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
      FilteringTreeStructure.Node selected = (FilteringTreeStructure.Node)((SimpleTree)myTree).getSelectedNode();
      return selected != null ? selected.getDelegate() : null;
    } else {
      final Object[] nodes = myTree.getSelectedNodes(Object.class, null);
      return nodes.length > 0 ? nodes[0] : null;
    }
  }

  public FilteringTreeStructure.Node getVisibleNodeFor(Object nodeObject) {
    FilteringTreeStructure structure = getFilteredStructure();
    return structure != null ? structure.getVisibleNodeFor(nodeObject) : null;
  }

  public Object getOriginalNode(Object node) {
    return ((FilteringTreeStructure.Node)node).getDelegate();
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
