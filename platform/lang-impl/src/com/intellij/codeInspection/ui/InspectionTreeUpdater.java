/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;

import javax.swing.tree.DefaultTreeModel;

/**
 * @author Dmitry Batkovich
 */
public class InspectionTreeUpdater {
  private final InspectionTree myTree;
  private final MergingUpdateQueue myUpdateQueue;

  public InspectionTreeUpdater(InspectionTree tree) {
    myTree = tree;
    myUpdateQueue = new MergingUpdateQueue("InspectionView", 100, true, tree);
  }

  public void update() {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      return;
    }
    myUpdateQueue.queue(new Update("TreeRepaint") {
      @Override
      public void run() {
        try {
          myTree.setQueueUpdate(true);
          ((DefaultTreeModel)myTree.getModel()).reload();
          myTree.revalidate();
          myTree.repaint();
          myTree.restoreExpansionAndSelection();
        } finally {
          myTree.setQueueUpdate(false);
        }
      }

      @Override
      public boolean canEat(Update update) {
        return true;
      }
    });
  }
}
