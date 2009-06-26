/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.*;

public class AbstractTreeUpdater implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.treeView.AbstractTreeUpdater");

  private final LinkedList<TreeUpdatePass> myNodeQueue = new LinkedList<TreeUpdatePass>();
  private final AbstractTreeBuilder myTreeBuilder;
  private final List<Runnable> myRunAfterUpdate = new ArrayList<Runnable>();
  private Runnable myRunBeforeUpdate;
  private final MergingUpdateQueue myUpdateQueue;

  private long myUpdateCount;

  public AbstractTreeUpdater(AbstractTreeBuilder treeBuilder) {
    myTreeBuilder = treeBuilder;
    final JTree tree = myTreeBuilder.getTree();
    myUpdateQueue = new MergingUpdateQueue("UpdateQueue", 300, tree.isShowing(), tree);
    final UiNotifyConnector uiNotifyConnector = new UiNotifyConnector(tree, myUpdateQueue);
    Disposer.register(this, myUpdateQueue);
    Disposer.register(this, uiNotifyConnector);
  }

  /**
   * @param delay update delay in milliseconds.
   */
  public void setDelay(int delay) {
    myUpdateQueue.setMergingTimeSpan(delay);
  }

  public boolean hasNodesToUpdate() {
    return myNodeQueue.size() > 0;
  }

  public void dispose() {
  }

  public synchronized void addSubtreeToUpdate(@NotNull DefaultMutableTreeNode rootNode) {
    addSubtreeToUpdate(new TreeUpdatePass(rootNode));
  }

  public synchronized void addSubtreeToUpdate(@NotNull TreeUpdatePass toAdd) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("addSubtreeToUpdate:" + toAdd.getNode());
    }

   assert !toAdd.isExpired();

    for (Iterator<TreeUpdatePass> iterator = myNodeQueue.iterator(); iterator.hasNext();) {
      final TreeUpdatePass passInQueue = iterator.next();
      if (passInQueue.getNode() == toAdd.getNode()) {
        toAdd.expire();
        return;
      } else if (toAdd.getNode().isNodeAncestor(passInQueue.getNode())) {
        toAdd.expire();
        return;
      } else if (passInQueue.getNode().isNodeAncestor(toAdd.getNode())) {
        iterator.remove();
        passInQueue.expire();
      }
    }

    long newUpdateCount = myUpdateCount + 1;

    final AbstractTreeUi ui = myTreeBuilder.getUi();
    final Collection<TreeUpdatePass> yielding = ui.getYeildingPasses();
    for (Iterator<TreeUpdatePass> iterator = yielding.iterator(); iterator.hasNext();) {
      TreeUpdatePass eachYielding = iterator.next();

      final DefaultMutableTreeNode eachNode = eachYielding.getCurrentNode();
      if (eachNode != null) {
        if (eachNode.isNodeAncestor(toAdd.getNode())) {
          toAdd.expire();
        } else {
          eachYielding.setSheduleStamp(newUpdateCount);
        }
      }
    }

    if (toAdd.isExpired()) return;


    myNodeQueue.add(toAdd);

    myUpdateCount = newUpdateCount;
    toAdd.setSheduleStamp(myUpdateCount);

    queue(new Update("ViewUpdate") {
      public boolean isExpired() {
        return myTreeBuilder.isDisposed();
      }

      public void run() {
        if (myTreeBuilder.getTreeStructure().hasSomethingToCommit()) {
          queue(this);
          return;
        }
        myTreeBuilder.getTreeStructure().commit();
        try {
          performUpdate();
        }
        catch(ProcessCanceledException e) {
          throw e;
        } catch(RuntimeException e) {
          LOG.error(myTreeBuilder.getClass().getName(), e);
        }
      }
    });
  }

  private void queue(Update update) {
    myUpdateQueue.queue(update);
  }

  /**
   * @deprecated use addSubtreeToUpdate instead
   * @param node
   */
  protected void updateSubtree(DefaultMutableTreeNode node) {
    myTreeBuilder.updateSubtree(node);
  }

  public synchronized void performUpdate() {
    if (myRunBeforeUpdate != null){
      myRunBeforeUpdate.run();
      myRunBeforeUpdate = null;
    }

    while(!myNodeQueue.isEmpty()){
      final TreeUpdatePass eachPass = myNodeQueue.removeFirst();
      beforeUpdate(eachPass).doWhenDone(new Runnable() {
        public void run() {
          myTreeBuilder.getUi().updateSubtree(eachPass);
        }
      });
    }

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

      invokeLater(runnable);
    }
  }

  protected void invokeLater(Runnable runnable) {
    final Application app = ApplicationManager.getApplication();
    if (app != null) {
      app.invokeLater(runnable);
    } else {
      UIUtil.invokeAndWaitIfNeeded(runnable);
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
    if (node != null){
      addSubtreeToUpdate(node);
      return true;
    }
    else{
      return false;
    }
  }

  public void cancelAllRequests(){
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
}
