package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public abstract class BeforeCheckinHandler {

  public enum ReturnResult {
    COMMIT, CANCEL, CLOSE_WINDOW
  }

  @Nullable
  public abstract RefreshableOnComponent getConfigurationPanel();

  public abstract ReturnResult perform(VirtualFile[] filesToBeCommited);
}
