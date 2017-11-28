/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.*;
import com.intellij.util.Alarm;
import com.intellij.util.Processor;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
class InspectionViewPsiTreeChangeAdapter extends PsiTreeChangeAdapter {
  private final static int MAX_UPDATES_FOR_CANCELLABLE_ACTION = 100;

  private final InspectionResultsView myView;
  private final MergingUpdateQueue myUpdater;

  private final Alarm myAlarm;

  public InspectionViewPsiTreeChangeAdapter(@NotNull InspectionResultsView view) {
    myView = view;
    myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, view);
    myUpdater = new MergingUpdateQueue("inspection.view.psi.update.listener",
                                       300,
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
              VfsUtilCore.iterateChildrenRecursively(file, VirtualFileFilter.ALL, files::add);
            }
            final Project project = view.getProject();

            final Runnable runnable = () -> {
              if (view.isDisposed()) return;
              synchronized (myView.getTreeStructureUpdateLock()) {
                InspectionTreeNode root = myView.getTree().getRoot();
                boolean[] needUpdateUI = {false};
                processNodesIfNeed(root, (node) -> {
                  if (node instanceof SuppressableInspectionTreeNode) {
                    RefEntity element = ((SuppressableInspectionTreeNode)node).getElement();
                    if (element instanceof RefElement) {
                      final SmartPsiElementPointer pointer = ((RefElement)element).getPointer();
                      VirtualFile strictVirtualFile = pointer.getVirtualFile();
                      if (strictVirtualFile == null || !strictVirtualFile.isValid()) {
                        final PsiFile file = pointer.getContainingFile();
                        if (file != null && file.isValid()) {
                          strictVirtualFile = file.getVirtualFile();
                        }
                      }
                      if (strictVirtualFile == null || files.contains(strictVirtualFile)) {
                        ((SuppressableInspectionTreeNode)node).dropCache(project);
                        if (!needUpdateUI[0]) {
                          needUpdateUI[0] = true;
                        }
                      }
                      return false;
                    }
                    else {
                      ((SuppressableInspectionTreeNode)node).dropCache(project);
                      if (!needUpdateUI[0]) {
                        needUpdateUI[0] = true;
                      }
                      return false;
                    }
                  }
                  return true;
                });
                if (needUpdateUI[0] && !myAlarm.isDisposed()) {
                  myAlarm.cancelAllRequests();
                  myAlarm.addRequest(() -> myView.resetTree(), 100, ModalityState.NON_MODAL);
                }
              }
            };

            if (updates.length > MAX_UPDATES_FOR_CANCELLABLE_ACTION) {
              ProgressManager.getInstance().executeNonCancelableSection(runnable);
            } else {
              runnable.run();
            }
          }

          @Override
          public void onCanceled(@NotNull ProgressIndicator indicator) {
            if (!myView.isDisposed()) {
              for (Update update : updates) {
                myUpdater.queue(update);
              }
            }
          }
        };
        ProgressIndicatorUtils.scheduleWithWriteActionPriority(myView.getTreeUpdater(), task);
      }
    };
  }

  @Override
  public void childAdded(@NotNull PsiTreeChangeEvent event) {
    processEventFileOrDir(event, false);
  }

  @Override
  public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
    processEventFileOrDir(event, true);
  }

  @Override
  public void beforeChildReplacement(@NotNull PsiTreeChangeEvent event) {
    processEventFileOrDir(event, true);
  }

  @Override
  public void childMoved(@NotNull PsiTreeChangeEvent event) {
    processEventFileOrDir(event, false);
  }

  @Override
  public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
    processEventFileOrDir(event, false);
  }

  @Override
  public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
    processEventFileOrDir(event, false);
  }

  private void processEventFileOrDir(@NotNull PsiTreeChangeEvent event, boolean eagerEvaluateFiles) {
    final PsiFile file = event.getFile();
    if (file != null) {
      VirtualFile vFile = file.getVirtualFile();
      if (vFile == null) return;
      invalidateFiles(vFile);
    }
    else {
      final PsiElement child = event.getChild();
      if (child instanceof PsiFileSystemItem) {
        final VirtualFile childFile = ((PsiFileSystemItem)child).getVirtualFile();
        if (childFile != null) {
          if (eagerEvaluateFiles) {
            Set<VirtualFile> files = new HashSet<>();
            VfsUtilCore.iterateChildrenRecursively(childFile, VirtualFileFilter.ALL, files::add);
            invalidateFiles(files.toArray(new VirtualFile[files.size()]));
          }
          else {
            invalidateFiles(childFile);
          }
        }
      }
    }
  }

  private static void processNodesIfNeed(InspectionTreeNode node, Processor<InspectionTreeNode> processor) {
    if (processor.process(node)) {
      final int count = node.getChildCount();
      for (int i = 0; i < count; i++) {
        processNodesIfNeed((InspectionTreeNode)node.getChildAt(i), processor);
      }
    }
  }

  private void invalidateFiles(VirtualFile... files) {
    for (VirtualFile file : files) {
      myUpdater.queue(new Update(file) {
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
  }
}
