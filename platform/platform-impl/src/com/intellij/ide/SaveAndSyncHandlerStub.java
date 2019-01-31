// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/**
 * Trivial implementation used in tests and in the headless mode.
 */
public class SaveAndSyncHandlerStub extends SaveAndSyncHandler {
  @Override
  public void scheduleSaveDocumentsAndProjectsAndApp(@Nullable Project project) {
  }

  @Override
  public void scheduleRefresh() {
  }

  @Override
  public void refreshOpenFiles() {
  }

  @Override
  public void blockSaveOnFrameDeactivation() {
  }

  @Override
  public void unblockSaveOnFrameDeactivation() {
  }

  @Override
  public void blockSyncOnFrameActivation() {
  }

  @Override
  public void unblockSyncOnFrameActivation() {
  }
}
