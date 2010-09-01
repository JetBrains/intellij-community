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
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.RefreshSession;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class SaveAndSyncHandler implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.SaveAndSyncHandler");
  private final Runnable myIdleListener;
  private final PropertyChangeListener myGeneralSettingsListener;

  public SaveAndSyncHandler(final FrameStateManager frameStateManager,
                            final FileDocumentManager fileDocumentManager,
                            final GeneralSettings generalSettings) {

    myIdleListener = new Runnable() {
      public void run() {
        if (generalSettings.isAutoSaveIfInactive() && canSyncOrSave()) {
          fileDocumentManager.saveAllDocuments();
        }
      }
    };


    IdeEventQueue.getInstance().addIdleListener(
      myIdleListener,
      generalSettings.getInactiveTimeout() * 1000
    );

    myGeneralSettingsListener = new PropertyChangeListener() {
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
      public void onFrameDeactivated() {
        if (canSyncOrSave()) {
          saveProjectsAndDocuments();
        }
      }

      public void onFrameActivated() {
        refreshFiles();
      }
    });

  }

  @NotNull
  public String getComponentName() {
    return "SaveAndSyncHandler";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    GeneralSettings.getInstance().removePropertyChangeListener(myGeneralSettingsListener);
    IdeEventQueue.getInstance().removeIdleListener(myIdleListener);
  }

  private static boolean canSyncOrSave() {
    return !LaterInvocator.isInModalContext() && !ProgressManager.getInstance().hasModalProgressIndicator();
  }

  // made public for tests
  public static void saveProjectsAndDocuments() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: save()");
    }
    if (ApplicationManager.getApplication().isDisposed()) return;
    
    if (GeneralSettings.getInstance().isSaveOnFrameDeactivation()) {
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

  private static void refreshFiles() {
    if (ApplicationManager.getApplication().isDisposed()) return;
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: synchronize()");
    }

    if (canSyncOrSave()) {
      refreshOpenFiles();
    }

    if (GeneralSettings.getInstance().isSyncOnFrameActivation()) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("refresh VFS");
      }
      VirtualFileManager.getInstance().refresh(true);
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("exit: synchronize()");
    }
  }

  public static void refreshOpenFiles() {
    // Refresh open files synchronously so it doesn't wait for potentially longish refresh request in the queue to finish
    final RefreshSession session = RefreshQueue.getInstance().createSession(false, false, null);

    for (Project project : ProjectManagerEx.getInstanceEx().getOpenProjects()) {
      VirtualFile[] files = FileEditorManager.getInstance(project).getSelectedFiles();
      for (VirtualFile file : files) {
        if (file instanceof NewVirtualFile) {
          session.addFile(file);
        }
      }
    }

    session.launch();
  }
}