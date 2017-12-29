// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;

import javax.swing.tree.DefaultTreeModel;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Dmitry Batkovich
 */
public class InspectionTreeUpdater {
  private final MergingUpdateQueue myUpdateQueue;
  private final InspectionResultsView myView;
  private final AtomicBoolean myDoUpdatePreviewPanel = new AtomicBoolean(false);

  public InspectionTreeUpdater(InspectionResultsView view) {
    myView = view;
    myUpdateQueue = new MergingUpdateQueue("InspectionView", 100, true, view, view);
    myUpdateQueue.setPassThrough(false);
  }

  public void updateWithPreviewPanel() {
    myDoUpdatePreviewPanel.set(true);
    update(false);
  }

  public void update(boolean force) {
    if (ApplicationManager.getApplication().isDispatchThread() && !force) {
      return;
    }
    myUpdateQueue.queue(new MyTreeUpdate());
  }

  private class MyTreeUpdate extends Update {
    public MyTreeUpdate() {
      super("inspection.view.update");
    }

    @Override
    public void run() {
      if (myView.isDisposed()) return;
      final InspectionTree tree = myView.getTree();
      try {
        tree.setQueueUpdate(true);
        ((DefaultTreeModel)tree.getModel()).reload();
        tree.restoreExpansionAndSelection(true);
        myView.openRightPanelIfNeed();
        if (myDoUpdatePreviewPanel.compareAndSet(true, false)) {
          myView.updateRightPanelLoading();
        }
      } finally {
        tree.setQueueUpdate(false);
      }
    }

    @Override
    public boolean canEat(Update update) {
      return true;
    }
  }
}
