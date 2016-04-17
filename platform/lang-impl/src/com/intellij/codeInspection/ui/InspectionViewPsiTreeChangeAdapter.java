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

import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.util.Alarm;
import com.intellij.util.Processor;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
class InspectionViewPsiTreeChangeAdapter extends PsiTreeChangeAdapter {
  private final InspectionResultsView myView;
  private final MergingUpdateQueue myUpdater;

  public InspectionViewPsiTreeChangeAdapter(InspectionResultsView view) {
    myView = view;
    myUpdater = new MergingUpdateQueue("inspection.view.psi.update.listener",
                           200,
                           true,
                           myView,
                           myView,
                           myView,
                           Alarm.ThreadToUse.POOLED_THREAD) {
      @Override
      protected void execute(@NotNull Update[] updates) {
        ReadTask task = new ReadTask() {
          @Override
          public void computeInReadAction(@NotNull ProgressIndicator indicator) throws ProcessCanceledException {
            indicator.setText("Updating inspection view tree...");
            Set<VirtualFile> files = new HashSet<>();
            for (Update update : updates) {
              VirtualFile file = (VirtualFile)update.getEqualityObjects()[0];
              files.add(file);
            }

            synchronized (myView.getTreeStructureUpdateLock()) {
              InspectionTreeNode root = myView.getTree().getRoot();
              boolean[] needUpdateUI = {false};
              processNodesIfNeed(root, (node) -> {
                if (node instanceof CachedInspectionTreeNode) {
                  indicator.checkCanceled();
                  RefEntity element = ((CachedInspectionTreeNode)node).getElement();
                  if (element instanceof RefElement) {
                    VirtualFile containingFile = ((RefElement)element).getPointer().getVirtualFile();
                    if (files.contains(containingFile)) {
                      ((CachedInspectionTreeNode)node).dropCache();
                      if (!needUpdateUI[0]) {
                        needUpdateUI[0] = true;
                      }
                      return false;
                    }
                  } else {
                    ((CachedInspectionTreeNode)node).dropCache();
                    if (!needUpdateUI[0]) {
                      needUpdateUI[0] = true;
                    }
                    return false;
                  }
                }
                return true;
              });
              if (needUpdateUI[0]) {
                myView.invalidate();
                myView.repaint();
                myView.syncRightPanel();
              }
            }
          }

          @Override
          public void onCanceled(@NotNull ProgressIndicator indicator) {
            if (!myView.isDisposed()) {
              ProgressIndicatorUtils.scheduleWithWriteActionPriority(this);
            }
          }
        };
        ProgressIndicatorUtils.scheduleWithWriteActionPriority(task);
      }
    };
  }

  @Override
  public void beforeChildAddition(@NotNull PsiTreeChangeEvent event) {
    psiChanged(event.getFile());
  }

  @Override
  public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
    psiChanged(event.getFile());
  }

  @Override
  public void beforeChildReplacement(@NotNull PsiTreeChangeEvent event) {
    psiChanged(event.getFile());
  }

  @Override
  public void beforeChildMovement(@NotNull PsiTreeChangeEvent event) {
    psiChanged(event.getFile());
  }

  @Override
  public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
    psiChanged(event.getFile());
  }

  @Override
  public void beforePropertyChange(@NotNull PsiTreeChangeEvent event) {
    psiChanged(event.getFile());
  }

  private void psiChanged(@Nullable PsiFile changedFile) {
    if (changedFile == null) return;
    VirtualFile vFile = changedFile.getVirtualFile();
    if (vFile == null) return;
    myUpdater.queue(new Update(vFile) {
      @Override
      public void run() {
        //do nothing
      }

      @Override
      public boolean canEat(Update update) {
        return false;
      }
    });
  }

  private static void processNodesIfNeed(InspectionTreeNode node, Processor<InspectionTreeNode> processor) {
    if (processor.process(node)) {
      for (int i = 0; i < node.getChildCount(); i++) {
        processNodesIfNeed((InspectionTreeNode)node.getChildAt(i), processor);
      }
    }
  }
}
