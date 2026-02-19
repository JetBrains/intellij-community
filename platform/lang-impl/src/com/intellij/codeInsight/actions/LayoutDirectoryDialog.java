// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class LayoutDirectoryDialog extends LayoutProjectCodeDialog implements DirectoryFormattingOptions {
  public LayoutDirectoryDialog(@NotNull Project project,
                               @NlsContexts.DialogTitle String title,
                               @NlsContexts.Label String text,
                               boolean enableOnlyVCSChangedTextCb)
  {
    super(project, title, text, enableOnlyVCSChangedTextCb);
  }

  @Override
  protected boolean shouldShowIncludeSubdirsCb() {
    return true;
  }

  public void setEnabledIncludeSubdirsCb(boolean isEnabled) {
    myIncludeSubdirsCb.setEnabled(isEnabled);
  }

  public void setSelectedIncludeSubdirsCb(boolean isSelected) {
    myIncludeSubdirsCb.setSelected(isSelected);
  }

  @Override
  public boolean isIncludeSubdirectories() {
    return myIncludeSubdirsCb.isSelected();
  }

}
