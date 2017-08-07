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
package com.intellij.ide;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.components.impl.stores.StoreUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.RefreshSession;
import com.intellij.util.SingleAlarm;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class SaveAndSyncHandlerImpl extends SaveAndSyncHandler implements Disposable {
  private static final Logger LOG = Logger.getInstance(SaveAndSyncHandler.class);

  private final Runnable myIdleListener;
  private final PropertyChangeListener myGeneralSettingsListener;
  private final GeneralSettings mySettings;
  private final ProgressManager myProgressManager;
  private final SingleAlarm myRefreshDelayAlarm = new SingleAlarm(this::doScheduledRefresh, 300, this);
  private final AtomicInteger myBlockSaveOnFrameDeactivationCount = new AtomicInteger();
  private final AtomicInteger myBlockSyncOnFrameActivationCount = new AtomicInteger();
  private volatile long myRefreshSessionId;

  public SaveAndSyncHandlerImpl(@NotNull GeneralSettings generalSettings,
                                @NotNull ProgressManager progressManager,
                                @NotNull FrameStateManager frameStateManager,
                                @NotNull FileDocumentManager fileDocumentManager) {
    mySettings = generalSettings;
    myProgressManager = progressManager;

    myIdleListener = () -> {
      if (mySettings.isAutoSaveIfInactive() && canSyncOrSave()) {
        TransactionGuard.submitTransaction(ApplicationManager.getApplication(), () -> ((FileDocumentManagerImpl)fileDocumentManager).saveAllDocuments(false));
      }
    };
    IdeEventQueue.getInstance().addIdleListener(myIdleListener, mySettings.getInactiveTimeout() * 1000);

    myGeneralSettingsListener = new PropertyChangeListener() {
      @Override
      public void propertyChange(@NotNull PropertyChangeEvent e) {
        if (GeneralSettings.PROP_INACTIVE_TIMEOUT.equals(e.getPropertyName())) {
          IdeEventQueue eventQueue = IdeEventQueue.getInstance();
          eventQueue.removeIdleListener(myIdleListener);
          Integer timeout = (Integer)e.getNewValue();
          eventQueue.addIdleListener(myIdleListener, timeout.intValue() * 1000);
        }
      }
    };
    mySettings.addPropertyChangeListener(myGeneralSettingsListener);

    frameStateManager.addListener(new FrameStateListener() {
      @Override
      public void onFrameDeactivated() {
        LOG.debug("save(): enter");
        TransactionGuard.submitTransaction(ApplicationManager.getApplication(), () -> {
          if (canSyncOrSave()) {
            saveProjectsAndDocuments();
          }
          LOG.debug("save(): exit");
        });
      }

      @Override
      public void onFrameActivated() {
        if (!ApplicationManager.getApplication().isDisposed() && mySettings.isSyncOnFrameActivation()) {
          scheduleRefresh();
        }
      }
    });
  }

  @Override
  public void dispose() {
    RefreshQueue.getInstance().cancelSession(myRefreshSessionId);
    mySettings.removePropertyChangeListener(myGeneralSettingsListener);
    IdeEventQueue.getInstance().removeIdleListener(myIdleListener);
  }

  private boolean canSyncOrSave() {
    return !LaterInvocator.isInModalContext() && !myProgressManager.hasModalProgressIndicator();
  }

  @Override
  public void saveProjectsAndDocuments() {
    if (!ApplicationManager.getApplication().isDisposed() &&
        mySettings.isSaveOnFrameDeactivation() &&
        myBlockSaveOnFrameDeactivationCount.get() == 0) {
      StoreUtil.saveDocumentsAndProjectsAndApp();
    }
  }

  @Override
  public void scheduleRefresh() {
    myRefreshDelayAlarm.cancelAndRequest();
  }

  private void doScheduledRefresh() {
    if (canSyncOrSave()) {
      refreshOpenFiles();
    }
    maybeRefresh(ModalityState.NON_MODAL);
  }

  public void maybeRefresh(@NotNull ModalityState modalityState) {
    if (myBlockSyncOnFrameActivationCount.get() == 0 && mySettings.isSyncOnFrameActivation()) {
      RefreshQueue queue = RefreshQueue.getInstance();
      queue.cancelSession(myRefreshSessionId);

      RefreshSession session = queue.createSession(true, true, null, modalityState);
      session.addAllFiles(ManagingFS.getInstance().getLocalRoots());
      myRefreshSessionId = session.getId();
      session.launch();
      LOG.debug("vfs refreshed");
    }
    else if (LOG.isDebugEnabled()) {
      LOG.debug("vfs refresh rejected, blocked: " + (myBlockSyncOnFrameActivationCount.get() != 0)
                + ", isSyncOnFrameActivation: " + mySettings.isSyncOnFrameActivation());
    }
  }

  @Override
  public void refreshOpenFiles() {
    List<VirtualFile> files = ContainerUtil.newArrayList();

    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      for (VirtualFile file : FileEditorManager.getInstance(project).getSelectedFiles()) {
        if (file instanceof NewVirtualFile) {
          files.add(file);
        }
      }
    }

    if (!files.isEmpty()) {
      // refresh open files synchronously so it doesn't wait for potentially longish refresh request in the queue to finish
      RefreshQueue.getInstance().refresh(false, false, null, files);
    }
  }

  @Override
  public void blockSaveOnFrameDeactivation() {
    LOG.debug("save blocked");
    myBlockSaveOnFrameDeactivationCount.incrementAndGet();
  }

  @Override
  public void unblockSaveOnFrameDeactivation() {
    myBlockSaveOnFrameDeactivationCount.decrementAndGet();
    LOG.debug("save unblocked");
  }

  @Override
  public void blockSyncOnFrameActivation() {
    LOG.debug("sync blocked");
    myBlockSyncOnFrameActivationCount.incrementAndGet();
  }

  @Override
  public void unblockSyncOnFrameActivation() {
    myBlockSyncOnFrameActivationCount.decrementAndGet();
    LOG.debug("sync unblocked");
  }
}