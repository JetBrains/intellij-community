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

package com.intellij.ide.util.treeView;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.*;

public class AbstractTreeUpdater implements Disposable, Activatable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.treeView.AbstractTreeUpdater");

  private final LinkedList<TreeUpdatePass> myNodeQueue = new LinkedList<TreeUpdatePass>();
  private final AbstractTreeBuilder myTreeBuilder;
  private final List<Runnable> myRunAfterUpdate = new ArrayList<Runnable>();
  private Runnable myRunBeforeUpdate;
  private final MergingUpdateQueue myUpdateQueue;

  private long myUpdateCount;
  private boolean myReleaseRequested;

  public AbstractTreeUpdater(AbstractTreeBuilder treeBuilder) {
    myTreeBuilder = treeBuilder;
    final JTree tree = myTreeBuilder.getTree();
    final JComponent component = tree instanceof TreeTableTree ? ((TreeTableTree)tree).getTreeTable() : tree;
    myUpdateQueue = new MergingUpdateQueue("UpdateQeue", 300, component.isShowing(), component) {
      @Override
      protected Alarm createAlarm(Alarm.ThreadToUse thread, Disposable parent) {
        return new Alarm(thread, parent) {
          @Override
          protected boolean isEdt() {
            return AbstractTreeUpdater.this.isEdt();
          }
        };
      }
    };
    myUpdateQueue.setRestartTimerOnAdd(false);

    final UiNotifyConnector uiNotifyConnector = new UiNotifyConnector(component, myUpdateQueue);
    Disposer.register(this, myUpdateQueue);
    Disposer.register(this, uiNotifyConnector);
  }

  /**
   * @param delay update delay in milliseconds.
   */
  public void setDelay(int delay) {
    myUpdateQueue.setMergingTimeSpan(delay);
  }

  public void setPassThroughMode(boolean passThroughMode) {
    myUpdateQueue.setPassThrough(passThroughMode);
  }

  public void setModalityStateComponent(JComponent c) {
    myUpdateQueue.setModalityStateComponent(c);
  }

  public boolean hasNodesToUpdate() {
    return myNodeQueue.size() > 0;
  }

  public void dispose() {
  }

  public synchronized void addSubtreeToUpdate(@NotNull DefaultMutableTreeNode rootNode) {
    addSubtreeToUpdate(new TreeUpdatePass(rootNode).setUpdateStamp(-1));
  }

  public synchronized void addSubtreeToUpdate(@NotNull TreeUpdatePass toAdd) {
    if (myReleaseRequested) return;

    assert !toAdd.isExpired();

    final AbstractTreeUi ui = myTreeBuilder.getUi();

    if (ui.isUpdatingNow(toAdd.getNode())) {
      toAdd.expire();
    }
    else {
      for (Iterator<TreeUpdatePass> iterator = myNodeQueue.iterator(); iterator.hasNext();) {
        final TreeUpdatePass passInQueue = iterator.next();

        if (passInQueue == toAdd) {
          toAdd.expire();
          break;
        }
        else if (passInQueue.getNode() == toAdd.getNode()) {
          toAdd.expire();
          break;
        }
        else if (toAdd.getNode().isNodeAncestor(passInQueue.getNode())) {
          toAdd.expire();
          break;
        }
        else if (passInQueue.getNode().isNodeAncestor(toAdd.getNode())) {
          iterator.remove();
          passInQueue.expire();
        }
      }
    }

    long newUpdateCount = toAdd.getUpdateStamp() == -1 ? myUpdateCount : myUpdateCount + 1;

    if (!toAdd.isExpired()) {
      final Collection<TreeUpdatePass> yielding = ui.getYeildingPasses();
      for (Iterator<TreeUpdatePass> iterator = yielding.iterator(); iterator.hasNext();) {
        TreeUpdatePass eachYielding = iterator.next();

        final DefaultMutableTreeNode eachNode = eachYielding.getCurrentNode();
        if (eachNode != null) {
          if (eachNode.isNodeAncestor(toAdd.getNode())) {
            eachYielding.setUpdateStamp(newUpdateCount);
          }
          else {
            toAdd.expire();
          }
        }
      }
    }


    if (toAdd.isExpired()) {
      requeueViewUpdateIfNeeded();
      return;
    }


    myNodeQueue.add(toAdd);

    myUpdateCount = newUpdateCount;
    toAdd.setUpdateStamp(myUpdateCount);

    requeueViewUpdate();
  }

  private void requeueViewUpdateIfNeeded() {
    //if (myTreeBuilder.getUi().isPassthroughMode()) return;

    if (myUpdateQueue.isEmpty() && !myNodeQueue.isEmpty()) {
      requeueViewUpdate();
    }
  }

  private void requeueViewUpdate() {
    queue(new Update("ViewUpdate") {
      public boolean isExpired() {
        return myTreeBuilder.isDisposed();
      }

      public void run() {
        if (myTreeBuilder.getTreeStructure().hasSomethingToCommit()) {
          myTreeBuilder.getTreeStructure().commit();
          requeueViewUpdateIfNeeded();
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

  private void queue(Update update) {
    if (isReleased()) return;

    myUpdateQueue.queue(update);
  }

  /**
   * @param node
   * @deprecated use addSubtreeToUpdate instead
   */
  protected void updateSubtree(DefaultMutableTreeNode node) {
    myTreeBuilder.updateSubtree(node);
  }

  public synchronized void performUpdate() {
    if (myRunBeforeUpdate != null) {
      myRunBeforeUpdate.run();
      myRunBeforeUpdate = null;
    }


    while (!myNodeQueue.isEmpty()) {
      if (isInPostponeMode()) break;


      final TreeUpdatePass eachPass = myNodeQueue.removeFirst();

      beforeUpdate(eachPass).doWhenDone(new Runnable() {
        public void run() {
          try {
            myTreeBuilder.getUi().updateSubtreeNow(eachPass, false);
          }
          catch (ProcessCanceledException e) {
            return;
          }
        }
      });
    }

    if (isReleased()) return;

    myTreeBuilder.getUi().maybeReady();

    if (myRunAfterUpdate != null) {
      final Runnable runnable = new Runnable() {
        public void run() {
          List<Runnable> runAfterUpdate = null;
          synchronized (myRunAfterUpdate) {
            if (!myRunAfterUpdate.isEmpty()) {
              runAfterUpdate = new ArrayList<Runnable>(myRunAfterUpdate);
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
  }

  private boolean isReleased() {
    return myTreeBuilder.getUi() == null;
  }

  protected void invokeLater(Runnable runnable) {
    if (myTreeBuilder.getUi().isPassthroughMode()) {
      runnable.run();
    } else {
      final Application app = ApplicationManager.getApplication();
      if (app != null) {
        app.invokeLater(runnable);
      }
      else {
        UIUtil.invokeAndWaitIfNeeded(runnable);
      }
    }
  }

  protected ActionCallback beforeUpdate(TreeUpdatePass pass) {
    return new ActionCallback.Done();
  }

  public boolean addSubtreeToUpdateByElement(Object element) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("addSubtreeToUpdateByElement:" + element);
    }

    DefaultMutableTreeNode node = myTreeBuilder.getNodeForElement(element);
    if (node != null) {
      addSubtreeToUpdate(node);
      return true;
    }
    else {
      return false;
    }
  }

  public void cancelAllRequests() {
    myNodeQueue.clear();
    myUpdateQueue.cancelAllUpdates();
  }

  public void runAfterUpdate(final Runnable runnable) {
    if (runnable == null) return;
    synchronized (myRunAfterUpdate) {
      myRunAfterUpdate.add(runnable);
    }
  }

  public synchronized void runBeforeUpdate(final Runnable runnable) {
    myRunBeforeUpdate = runnable;
  }

  public long getUpdateCount() {
    return myUpdateCount;
  }

  public boolean isRerunNeededFor(TreeUpdatePass pass) {
    return pass.getUpdateStamp() < getUpdateCount();
  }

  public boolean isInPostponeMode() {
    return !myUpdateQueue.isActive() && !myUpdateQueue.isPassThrough();
  }

  public void showNotify() {
    myUpdateQueue.showNotify();
  }

  public void hideNotify() {
    myUpdateQueue.hideNotify();
  }

  protected boolean isEdt() {
    return Alarm.isEventDispatchThread();
  }

  @Override
  public String toString() {
    return "AbstractTreeUpdater updateCount=" + myUpdateCount + " queue=[" + myUpdateQueue.toString() + "] " + " nodeQueue=" + myNodeQueue;
  }

  public void flush() {
    myUpdateQueue.sendFlush();
  }

  public boolean isEnqueuedToUpdate(DefaultMutableTreeNode node) {
    Iterator<TreeUpdatePass> nodes = myNodeQueue.iterator();
    while (nodes.hasNext()) {
      TreeUpdatePass each = nodes.next();
      if (each.willUpdate(node)) return true;
    }
    return false;
  }

  public final void queueSelection(final SelectionRequest request) {
    queue(new Update("UserSelection", Update.LOW_PRIORITY) {
      public void run() {
        request.execute(myTreeBuilder.getUi());
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

  public void requestRelease() {
    myReleaseRequested = true;

    reset();

    myUpdateQueue.deactivate();
  }

  public void reset() {
    TreeUpdatePass[] passes = myNodeQueue.toArray(new TreeUpdatePass[myNodeQueue.size()]);
    myNodeQueue.clear();
    myUpdateQueue.cancelAllUpdates();

    for (TreeUpdatePass each : passes) {
      myTreeBuilder.getUi().addToCancelled(each.getNode());
    }
  }
}
