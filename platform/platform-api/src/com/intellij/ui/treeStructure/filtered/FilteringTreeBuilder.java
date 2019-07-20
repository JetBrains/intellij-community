// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.Comparator;

/**
 * @deprecated use {@link com.intellij.ui.tree.AsyncTreeModel} and {@link com.intellij.ui.tree.StructureTreeModel} instead.
 */
@ApiStatus.ScheduledForRemoval
@Deprecated
public class FilteringTreeBuilder extends AbstractTreeBuilder {

  private Object myLastSuccessfulSelect;
  private final Tree myTree;

  private MergingUpdateQueue myRefilterQueue;

  public FilteringTreeBuilder(Tree tree,
                              ElementFilter filter,
                              AbstractTreeStructure structure,
                              @Nullable Comparator<? super NodeDescriptor> comparator) {
    super(tree,
          (DefaultTreeModel)tree.getModel(),
          structure instanceof FilteringTreeStructure ? structure
                                                      : new FilteringTreeStructure(filter, structure),
          comparator);

    myTree = tree;
    initRootNode();

    if (filter instanceof ElementFilter.Active) {
      ((ElementFilter.Active)filter).addListener(new ElementFilter.Listener() {
        @NotNull
        @Override
        public Promise<?> update(final Object preferredSelection, final boolean adjustSelection, final boolean now) {
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

  /**
   * @deprecated use {@link #refilter(Object, boolean, boolean)}
   */
  @NotNull
  @Deprecated
  public ActionCallback refilter() {
    return Promises.toActionCallback(refilter(null, true, false));
  }

  @SuppressWarnings("UnusedReturnValue")
  @NotNull
  public Promise<?> refilterAsync() {
    return refilter(null, true, false);
  }

  @NotNull
  public Promise<?> refilter(@Nullable final Object preferredSelection, final boolean adjustSelection, final boolean now) {
    if (myRefilterQueue != null) {
      myRefilterQueue.cancelAllUpdates();
    }

    AsyncPromise<?> result = new AsyncPromise<>();
    final Runnable afterCancelUpdate = new Runnable() {
      @Override
      public void run() {
        if (myRefilterQueue == null || now) {
          refilterNow(preferredSelection, adjustSelection)
            .onSuccess(o -> result.setResult(null));
        }
        else {
          myRefilterQueue.queue(new Update(this) {
            @Override
            public void run() {
              refilterNow(preferredSelection, adjustSelection);
            }

            @Override
            public void setRejected() {
              super.setRejected();
              result.setResult(null);
            }
          });
        }
      }
    };

    if (!isDisposed()) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        getUi().cancelUpdate().doWhenProcessed(afterCancelUpdate);
      }
      else {
        afterCancelUpdate.run();
      }
    }

    return result;
  }


  @NotNull
  protected Promise<?> refilterNow(Object preferredSelection, boolean adjustSelection) {
    final ActionCallback selectionDone = new ActionCallback();

    getFilteredStructure().refilter();
    getUi().updateSubtree(getRootNode(), false);
    final Runnable selectionRunnable = () -> {
      revalidateTree();

      Object toSelect = preferredSelection != null ? preferredSelection : myLastSuccessfulSelect;

      if (adjustSelection && toSelect != null) {
        final FilteringTreeStructure.FilteringNode nodeToSelect = getFilteredStructure().getVisibleNodeFor(toSelect);

        if (nodeToSelect != null) {
          select(nodeToSelect, () -> {
            if (getSelectedElements().contains(nodeToSelect)) {
              myLastSuccessfulSelect = getOriginalNode(nodeToSelect);
            }
            selectionDone.setDone();
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
    };
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      queueUpdate().doWhenProcessed(selectionRunnable);
    }
    else {
      selectionRunnable.run();
    }

    AsyncPromise<?> result = new AsyncPromise<>();
    selectionDone
      .doWhenDone(new Runnable() {
      @Override
      public void run() {
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          scrollSelectionToVisible(new Runnable() {
            @Override
            public void run() {
              getReady(this)
              .doWhenDone(() -> result.setResult(null))
              .doWhenRejected(s -> result.setError(s));
            }
          }, false);
        }
        else {
          result.setResult(null);
        }
      }
    })
      .doWhenRejected(() -> result.cancel(true));
    return result;
  }

  public void revalidateTree() {
    revalidateTree(myTree);
  }

  public static void revalidateTree(Tree tree) {
    TreeUtil.invalidateCacheAndRepaint(tree.getUI());
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
