package com.intellij.ide.util.treeView;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Iterator;
import java.util.LinkedList;

public class AbstractTreeUpdater implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.treeView.AbstractTreeUpdater");

  private LinkedList<DefaultMutableTreeNode> myNodesToUpdate = new LinkedList<DefaultMutableTreeNode>();
  private final AbstractTreeBuilder myTreeBuilder;
  private Runnable myRunAfterUpdate;
  private Runnable myRunBeforeUpdate;
  private MergingUpdateQueue myUpdateQueue;

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

  public void dispose() {
  }

  public void addSubtreeToUpdate(@NotNull DefaultMutableTreeNode rootNode) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("addSubtreeToUpdate:" + rootNode);
    }

    for (Iterator<DefaultMutableTreeNode> iterator = myNodesToUpdate.iterator(); iterator.hasNext();) {
      DefaultMutableTreeNode node = iterator.next();
      if (rootNode.isNodeAncestor(node)){
        return;
      }
      else if (node.isNodeAncestor(rootNode)){
        iterator.remove();
      }
    }
    myNodesToUpdate.add(rootNode);

    myUpdateQueue.queue(new Update("ViewUpdate") {
      public boolean isExpired() {
        return myTreeBuilder.isDisposed();
      }

      public void run() {
        if (myTreeBuilder.getTreeStructure().hasSomethingToCommit()) {
          myUpdateQueue.queue(this);
          return;
        }
        myTreeBuilder.getTreeStructure().commit();
        try {
          performUpdate();
        }
        catch(RuntimeException e) {
          LOG.error(myTreeBuilder.getClass().getName(), e);
        }
      }
    });
  }

  protected void updateSubtree(DefaultMutableTreeNode node) {
    myTreeBuilder.updateSubtree(node);
  }

  public void performUpdate() {
    if (myRunBeforeUpdate != null){
      myRunBeforeUpdate.run();
    }

    while(!myNodesToUpdate.isEmpty()){
      DefaultMutableTreeNode node = myNodesToUpdate.removeFirst();
      updateSubtree(node);
    }

    if (myRunAfterUpdate != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            synchronized (AbstractTreeUpdater.this) {
              if (myRunAfterUpdate != null) {
                myRunAfterUpdate.run();
                myRunAfterUpdate = null;
              }
            }
          }
        });
    }
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

  public boolean hasRequestsForUpdate() {
    return myUpdateQueue.containsUpdateOf(Update.LOW_PRIORITY);
  }

  public void cancelAllRequests(){
    myNodesToUpdate.clear();
    myUpdateQueue.cancelAllUpdates();
  }

  public synchronized void runAfterUpdate(final Runnable runnable) {
    myRunAfterUpdate = runnable;
  }

  public synchronized void runBeforeUpdate(final Runnable runnable) {
    myRunBeforeUpdate = runnable;
  }
}
