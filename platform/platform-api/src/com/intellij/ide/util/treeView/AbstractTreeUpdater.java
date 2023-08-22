// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.util.treeView;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.*;

/**
 * @deprecated use {@link com.intellij.ui.tree.AsyncTreeModel} and {@link com.intellij.ui.tree.StructureTreeModel} instead.
 */
@Deprecated(forRemoval = true)
public class AbstractTreeUpdater implements Disposable, Activatable {
  private static final Logger LOG = Logger.getInstance(AbstractTreeUpdater.class);

  private final LinkedList<TreeUpdatePass> myNodeQueue = new LinkedList<>();
  private final AbstractTreeBuilder myTreeBuilder;
  private final List<Runnable> myRunAfterUpdate = new ArrayList<>();
  private final MergingUpdateQueue myUpdateQueue;

  private long myUpdateCount;
  private boolean myReleaseRequested;

  public AbstractTreeUpdater(@NotNull AbstractTreeBuilder treeBuilder) {
    myTreeBuilder = treeBuilder;
    final JTree tree = myTreeBuilder.getTree();
    final JComponent component = tree instanceof TreeTableTree ? ((TreeTableTree)tree).getTreeTable() : tree;
    myUpdateQueue = new MergingUpdateQueue("UpdateQueue", 100, component.isShowing(), component);
    myUpdateQueue.setRestartTimerOnAdd(false);

    final UiNotifyConnector uiNotifyConnector = UiNotifyConnector.installOn(component, myUpdateQueue);
    Disposer.register(this, myUpdateQueue);
    Disposer.register(this, uiNotifyConnector);
  }

  /**
   * @param delay update delay in milliseconds.
   */
  public void setDelay(int delay) {
    myUpdateQueue.setMergingTimeSpan(delay);
  }

  void setPassThroughMode(boolean passThroughMode) {
    myUpdateQueue.setPassThrough(passThroughMode);
  }

  void setModalityStateComponent(JComponent c) {
    myUpdateQueue.setModalityStateComponent(c);
  }

  public ModalityState getModalityState() {
    return myUpdateQueue.getModalityState();
  }

  public boolean hasNodesToUpdate() {
    return !myNodeQueue.isEmpty() || !myUpdateQueue.isEmpty();
  }

  @Override
  public void dispose() {
  }

  /**
   * @deprecated use {@link AbstractTreeBuilder#queueUpdateFrom(Object, boolean)}
   */
  @Deprecated(forRemoval = true)
  public synchronized void addSubtreeToUpdate(@NotNull DefaultMutableTreeNode rootNode) {
    addSubtreeToUpdate(new TreeUpdatePass(rootNode).setUpdateStamp(-1));
  }

  /**
   * @deprecated use {@link AbstractTreeBuilder#queueUpdateFrom(Object, boolean)}
   */
  @Deprecated(forRemoval = true)
  synchronized void requeue(@NotNull TreeUpdatePass toAdd) {
    addSubtreeToUpdate(toAdd.setUpdateStamp(-1));
  }

  /**
   * @deprecated use {@link AbstractTreeBuilder#queueUpdateFrom(Object, boolean)}
   */
  @Deprecated(forRemoval = true)
  synchronized void addSubtreeToUpdate(@NotNull TreeUpdatePass toAdd) {
    if (myReleaseRequested) return;

    assert !toAdd.isExpired();

    final AbstractTreeUi ui = myTreeBuilder.getUi();
    if (ui == null) return;

    if (ui.isUpdatingChildrenNow(toAdd.getNode())) {
      toAdd.expire();
    }
    else {
      for (Iterator<TreeUpdatePass> iterator = myNodeQueue.iterator(); iterator.hasNext();) {
        final TreeUpdatePass passInQueue = iterator.next();

        boolean isMatchingPass =
          toAdd.isUpdateStructure() == passInQueue.isUpdateStructure() && toAdd.isUpdateChildren() == passInQueue.isUpdateChildren();
        if (isMatchingPass) {
          if (passInQueue == toAdd) {
            toAdd.expire();
            break;
          }
          if (passInQueue.getNode() == toAdd.getNode()) {
            toAdd.expire();
            break;
          }
          if (toAdd.getNode().isNodeAncestor(passInQueue.getNode())) {
            toAdd.expire();
            break;
          }
          if (passInQueue.getNode().isNodeAncestor(toAdd.getNode())) {
            iterator.remove();
            passInQueue.expire();
          }
        }
      }
    }


    if (toAdd.getUpdateStamp() >= 0) {
      Object element = ui.getElementFor(toAdd.getNode());
      if ((element == null || !ui.isParentLoadingInBackground(element)) && !ui.isParentUpdatingChildrenNow(toAdd.getNode())) {
        toAdd.setUpdateStamp(-1);
      }
    }

    long newUpdateCount = toAdd.getUpdateStamp() == -1 ? myUpdateCount : myUpdateCount + 1;

    if (!toAdd.isExpired()) {
      final Collection<TreeUpdatePass> yielding = ui.getYeildingPasses();
      for (TreeUpdatePass eachYielding : yielding) {
        final DefaultMutableTreeNode eachNode = eachYielding.getCurrentNode();
        if (eachNode != null) {
          if (eachNode.isNodeAncestor(toAdd.getNode())) {
            eachYielding.setUpdateStamp(newUpdateCount);
          }
        }
      }
    }


    if (toAdd.isExpired()) {
      reQueueViewUpdateIfNeeded();
      return;
    }


    myNodeQueue.add(toAdd);
    ui.addActivity();

    myUpdateCount = newUpdateCount;
    toAdd.setUpdateStamp(myUpdateCount);

    reQueueViewUpdate();
  }

  private void reQueueViewUpdateIfNeeded() {
    if (myUpdateQueue.isEmpty() && !myNodeQueue.isEmpty()) {
      reQueueViewUpdate();
    }
  }

  private void reQueueViewUpdate() {
    queue(new Update("ViewUpdate") {
      @Override
      public boolean isExpired() {
        return myTreeBuilder.isDisposed();
      }

      @Override
      public void run() {
        AbstractTreeStructure structure = myTreeBuilder.getTreeStructure();
        if (structure.hasSomethingToCommit()) {
          structure.asyncCommit().doWhenDone(new TreeRunnable("AbstractTreeUpdater.reQueueViewUpdate") {
            @Override
            public void perform() {
              reQueueViewUpdateIfNeeded();
            }
          });
          return;
        }
        try {
          performUpdate();
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (RuntimeException e) {
          LOG.error(myTreeBuilder.getClass().getName(), e);
        }
      }
    });
  }

  private void queue(@NotNull Update update) {
    if (isReleased()) return;

    myUpdateQueue.queue(update);
  }

  public synchronized void performUpdate() {
    while (!myNodeQueue.isEmpty()) {
      if (isInPostponeMode()) break;

      final TreeUpdatePass eachPass = myNodeQueue.removeFirst();

      beforeUpdate(eachPass).doWhenDone(new TreeRunnable("AbstractTreeUpdater.performUpdate") {
        @Override
        public void perform() {
          try {
            AbstractTreeUi ui = myTreeBuilder.getUi();
            if (ui != null) ui.updateSubtreeNow(eachPass, false);
          }
          catch (ProcessCanceledException ignored) {
          }
        }
      });
    }

    AbstractTreeUi ui = myTreeBuilder.getUi();
    if (ui == null) return;

    ui.maybeReady();

    maybeRunAfterUpdate();
  }

  private void maybeRunAfterUpdate() {
    final Runnable runnable = new TreeRunnable("AbstractTreeUpdater.maybeRunAfterUpdate") {
      @Override
      public void perform() {
        List<Runnable> runAfterUpdate = null;
        synchronized (myRunAfterUpdate) {
          if (!myRunAfterUpdate.isEmpty()) {
            runAfterUpdate = new ArrayList<>(myRunAfterUpdate);
            myRunAfterUpdate.clear();
          }
        }
        if (runAfterUpdate != null) {
          for (Runnable r : runAfterUpdate) {
            r.run();
          }
        }
      }
    };

    myTreeBuilder.getReady(this).doWhenDone(runnable);
  }

  private boolean isReleased() {
    return myTreeBuilder.getUi() == null;
  }

  protected ActionCallback beforeUpdate(TreeUpdatePass pass) {
    return ActionCallback.DONE;
  }

  /**
   * @deprecated use {@link AbstractTreeBuilder#queueUpdateFrom(Object, boolean)}
   */
  @Deprecated(forRemoval = true)
  public boolean addSubtreeToUpdateByElement(@NotNull Object element) {
    DefaultMutableTreeNode node = myTreeBuilder.getNodeForElement(element);
    if (node != null) {
      myTreeBuilder.queueUpdateFrom(element, false);
      return true;
    }
    else {
      return false;
    }
  }

  public synchronized void cancelAllRequests() {
    myNodeQueue.clear();
    myUpdateQueue.cancelAllUpdates();
  }

  public void runAfterUpdate(final Runnable runnable) {
    if (runnable != null) {
      synchronized (myRunAfterUpdate) {
        myRunAfterUpdate.add(runnable);
      }
    }
  }

  public synchronized long getUpdateCount() {
    return myUpdateCount;
  }

  boolean isRerunNeededFor(TreeUpdatePass pass) {
    return pass.getUpdateStamp() < getUpdateCount();
  }

  boolean isInPostponeMode() {
    return !myUpdateQueue.isActive() && !myUpdateQueue.isPassThrough();
  }

  @Override
  public void showNotify() {
    myUpdateQueue.showNotify();
  }

  @Override
  public void hideNotify() {
    myUpdateQueue.hideNotify();
  }

  @NonNls
  @Override
  public synchronized String toString() {
    return "AbstractTreeUpdater updateCount=" + myUpdateCount + " queue=[" + myUpdateQueue + "] " + " nodeQueue=" + myNodeQueue + " builder=" + myTreeBuilder;
  }

  public void flush() {
    myUpdateQueue.sendFlush();
  }

  synchronized boolean isEnqueuedToUpdate(DefaultMutableTreeNode node) {
    for (TreeUpdatePass pass : myNodeQueue) {
      if (pass.willUpdate(node)) return true;
    }
    return false;
  }

  final void queueSelection(final SelectionRequest request) {
    queue(new Update("UserSelection", Update.LOW_PRIORITY) {
      @Override
      public void run() {
        AbstractTreeUi ui = myTreeBuilder.getUi();
        if (ui != null) {
          request.execute(ui);
        }
      }

      @Override
      public boolean isExpired() {
        return myTreeBuilder.isDisposed();
      }

      @Override
      public void setRejected() {
        request.reject();
      }
    });
  }

  public synchronized void requestRelease() {
    myReleaseRequested = true;

    reset();

    myUpdateQueue.deactivate();
  }

  public void reset() {
    TreeUpdatePass[] passes;
    synchronized (this) {
      passes = myNodeQueue.toArray(new TreeUpdatePass[0]);
      myNodeQueue.clear();
    }
    myUpdateQueue.cancelAllUpdates();

    AbstractTreeUi ui = myTreeBuilder.getUi();
    if (ui != null) {
      for (TreeUpdatePass each : passes) {
        ui.addToCancelled(each.getNode());
      }
    }
  }
}
