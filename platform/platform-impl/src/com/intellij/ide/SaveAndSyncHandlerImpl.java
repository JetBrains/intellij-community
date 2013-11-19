/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.RefreshSession;
import com.intellij.util.Alarm;
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
public class SaveAndSyncHandlerImpl implements ApplicationComponent, SaveAndSyncHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.SaveAndSyncHandler");

  private final Runnable myIdleListener;
  private final PropertyChangeListener myGeneralSettingsListener;
  private final ProgressManager myProgressManager;

  private final AtomicInteger myBlockSaveOnFrameDeactivationCount = new AtomicInteger();
  private final AtomicInteger myBlockSyncOnFrameActivationCount = new AtomicInteger();
  private final Alarm myRefreshDelayAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  private long myRefreshSessionId = 0;

  public static SaveAndSyncHandlerImpl getInstance(){
    return (SaveAndSyncHandlerImpl) ApplicationManager.getApplication().getComponent(SaveAndSyncHandler.class);
  }

  public SaveAndSyncHandlerImpl(final FrameStateManager frameStateManager,
                                final FileDocumentManager fileDocumentManager,
                                final GeneralSettings generalSettings,
                                final ProgressManager progressManager) {
    myProgressManager = progressManager;

    myIdleListener = new Runnable() {
      @Override
      public void run() {
        if (generalSettings.isAutoSaveIfInactive() && canSyncOrSave()) {
          ((FileDocumentManagerImpl)fileDocumentManager).saveAllDocuments(false);
        }
      }
    };

    IdeEventQueue.getInstance().addIdleListener(
      myIdleListener,
      generalSettings.getInactiveTimeout() * 1000
    );

    myGeneralSettingsListener = new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent e) {
        if (GeneralSettings.PROP_INACTIVE_TIMEOUT.equals(e.getPropertyName())) {
          IdeEventQueue eventQueue = IdeEventQueue.getInstance();
          eventQueue.removeIdleListener(myIdleListener);
          Integer timeout = (Integer)e.getNewValue();
          eventQueue.addIdleListener(myIdleListener, timeout.intValue() * 1000);
        }
      }
    };
    generalSettings.addPropertyChangeListener(myGeneralSettingsListener);

    frameStateManager.addListener(new FrameStateListener() {
      @Override
      public void onFrameDeactivated() {
        if (canSyncOrSave()) {
          saveProjectsAndDocuments();
        }
      }

      @Override
      public void onFrameActivated() {
        refreshFiles();
      }
    });
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "SaveAndSyncHandler";
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
    GeneralSettings.getInstance().removePropertyChangeListener(myGeneralSettingsListener);
    IdeEventQueue.getInstance().removeIdleListener(myIdleListener);
  }

  private boolean canSyncOrSave() {
    return !LaterInvocator.isInModalContext() && !myProgressManager.hasModalProgressIndicator();
  }

  // made public for tests
  public void saveProjectsAndDocuments() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: save()");
    }
    if (ApplicationManager.getApplication().isDisposed()) return;

    if (myBlockSaveOnFrameDeactivationCount.get() == 0 && GeneralSettings.getInstance().isSaveOnFrameDeactivation()) {
      FileDocumentManager.getInstance().saveAllDocuments();

      Project[] openProjects = ProjectManagerEx.getInstanceEx().getOpenProjects();
      for (Project project : openProjects) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("save project: " + project);
        }
        project.save();
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("save application settings");
      }
      ApplicationManagerEx.getApplicationEx().saveSettings();
      if (LOG.isDebugEnabled()) {
        LOG.debug("exit: save()");
      }
    }
  }

  private void refreshFiles() {
    if (ApplicationManager.getApplication().isDisposed() || !GeneralSettings.getInstance().isSyncOnFrameActivation()) {
      return;
    }

    LOG.debug("enter: refreshFiles()");
    myRefreshDelayAlarm.cancelAllRequests();
    myRefreshDelayAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (canSyncOrSave()) {
          refreshOpenFiles();
        }
        maybeRefresh(ModalityState.NON_MODAL);
      }
    }, 300, ModalityState.NON_MODAL);
    LOG.debug("exit: refreshFiles()");
  }

  public void maybeRefresh(@NotNull ModalityState modalityState) {
    if (myBlockSyncOnFrameActivationCount.get() == 0 && GeneralSettings.getInstance().isSyncOnFrameActivation()) {
      LOG.debug("VFS refresh started");

      RefreshQueue queue = RefreshQueue.getInstance();
      queue.cancelSession(myRefreshSessionId);

      RefreshSession session = queue.createSession(true, true, null, modalityState);
      session.addAllFiles(ManagingFS.getInstance().getLocalRoots());
      myRefreshSessionId = session.getId();
      session.launch();

      LOG.debug("VFS refresh finished");
    }
  }

  public static void refreshOpenFiles() {
    List<VirtualFile> files = ContainerUtil.newArrayList();
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      VirtualFile[] projectFiles = FileEditorManager.getInstance(project).getSelectedFiles();
      for (VirtualFile file : projectFiles) {
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
    myBlockSaveOnFrameDeactivationCount.incrementAndGet();
  }

  @Override
  public void unblockSaveOnFrameDeactivation() {
    myBlockSaveOnFrameDeactivationCount.decrementAndGet();
  }

  @Override
  public void blockSyncOnFrameActivation() {
    myBlockSyncOnFrameActivationCount.incrementAndGet();
  }

  @Override
  public void unblockSyncOnFrameActivation() {
    myBlockSyncOnFrameActivationCount.decrementAndGet();
  }
}
