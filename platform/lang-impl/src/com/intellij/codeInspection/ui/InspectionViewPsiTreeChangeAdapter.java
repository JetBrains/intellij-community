/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.reference.RefElement;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.vfs.AsyncVfsEventsListener;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

class InspectionViewPsiTreeChangeAdapter implements AsyncVfsEventsListener {
  private final InspectionResultsView myView;
  private final Alarm myAlarm;
  private final Set<VirtualFile> myUnPresentEditedFiles = ContainerUtil.createWeakSet();

  public InspectionViewPsiTreeChangeAdapter(@NotNull InspectionResultsView view) {
    myView = view;
    myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, view);
  }

  @Override
  public void filesChanged(@NotNull List<VFileEvent> events) {
    //TODO filter out non project files
    boolean someFilesWereDeletedOrReplaced = false;
    Set<VirtualFile> filesToCheck = new THashSet<>();
    for (VFileEvent event : events) {
      if (!someFilesWereDeletedOrReplaced && (event instanceof VFileDeleteEvent || event instanceof VFileMoveEvent)) {
        someFilesWereDeletedOrReplaced = true;
      }
      if ((event instanceof VFileContentChangeEvent || event instanceof VFilePropertyChangeEvent) && !myUnPresentEditedFiles.contains(event.getFile())) {
        filesToCheck.add(event.getFile());
      }
    }
    if (filesToCheck.isEmpty() && !someFilesWereDeletedOrReplaced) return;

    boolean[] needUpdateUI = {false};
    if (someFilesWereDeletedOrReplaced) {
      //TODO combine 1
      synchronized (myView.getTreeStructureUpdateLock()) {
        InspectionTreeNode root = myView.getTree().getRoot();
        processNodesIfNeed(root, (node) -> {
          if (myView.isDisposed()) {
            return false;
          }

          if (node instanceof SuppressableInspectionTreeNode) {
            RefElement element = ObjectUtils.tryCast(((SuppressableInspectionTreeNode)node).getElement(), RefElement.class);
            if (element != null) {
              VirtualFile vFile = element.getPointer().getVirtualFile();
              if (vFile == null || !vFile.isValid()) {
                ((SuppressableInspectionTreeNode)node).dropCache();
                if (!needUpdateUI[0]) {
                  needUpdateUI[0] = true;
                }
              }
            }
          }

          return true;
        });
      }
    }

    Set<VirtualFile> unPresentFiles = new HashSet<>(filesToCheck);
    if (!filesToCheck.isEmpty()) {
      //TODO combine 2
      synchronized (myView.getTreeStructureUpdateLock()) {
        InspectionTreeNode root = myView.getTree().getRoot();
        processNodesIfNeed(root, (node) -> {
          if (myView.isDisposed()) {
            return false;
          }

          if (node instanceof SuppressableInspectionTreeNode) {
            RefElement element = ObjectUtils.tryCast(((SuppressableInspectionTreeNode)node).getElement(), RefElement.class);
            if (element != null) {
              VirtualFile vFile = element.getPointer().getVirtualFile();
              if (filesToCheck.contains(vFile)) {
                unPresentFiles.remove(vFile);
                ((SuppressableInspectionTreeNode)node).dropCache();
                if (!needUpdateUI[0]) {
                  needUpdateUI[0] = true;
                }
              }
            }
          }

          return true;
        });
      }
    }

    myUnPresentEditedFiles.addAll(unPresentFiles);
    if (needUpdateUI[0] && !myAlarm.isDisposed()) {
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(() -> myView.resetTree(), 100, ModalityState.NON_MODAL);
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
}
