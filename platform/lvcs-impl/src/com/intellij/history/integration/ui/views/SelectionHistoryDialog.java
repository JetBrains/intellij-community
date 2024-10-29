// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.history.integration.ui.views;

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.ui.models.FileHistoryDialogModel;
import com.intellij.history.integration.ui.models.SelectionHistoryDialogModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class SelectionHistoryDialog extends FileHistoryDialog {
  private final int myFrom;
  private final int myTo;

  public SelectionHistoryDialog(@NotNull Project p, IdeaGateway gw, VirtualFile f, int from, int to) {
    super(p, gw, f, false);
    myFrom = from;
    myTo = to;
    init();
  }

  @Override
  protected FileHistoryDialogModel createModel(LocalHistoryFacade vcs) {
    return new SelectionHistoryDialogModel(myProject, myGateway, vcs, myFile, myFrom, myTo);
  }
}
