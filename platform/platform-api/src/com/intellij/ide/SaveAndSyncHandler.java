// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author Kirill Likhodedov
 */
public abstract class SaveAndSyncHandler {
  @NotNull
  public static SaveAndSyncHandler getInstance() {
    return ApplicationManager.getApplication().getComponent(SaveAndSyncHandler.class);
  }

  /**
   * Schedule to save documents, all opened projects and application.
   *
   * Save is not performed immediately and not finished on method call return.
   */
  public abstract void scheduleSaveDocumentsAndProjectsAndApp();

  @SuppressWarnings("MethodMayBeStatic")
  @Deprecated
  public final void saveProjectsAndDocuments() {
    // used only by https://plugins.jetbrains.com/plugin/11072-openjml-esc
    // so, just save documents and nothing more, to simplify SaveAndSyncHandlerImpl
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  public abstract void scheduleRefresh();

  public abstract void refreshOpenFiles();

  public abstract void blockSaveOnFrameDeactivation();

  public abstract void unblockSaveOnFrameDeactivation();

  public abstract void blockSyncOnFrameActivation();

  public abstract void unblockSyncOnFrameActivation();
}
