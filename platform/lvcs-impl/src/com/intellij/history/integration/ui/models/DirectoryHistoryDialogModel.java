// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.history.integration.ui.models;

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.revertion.DifferenceReverter;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.history.integration.ui.views.DirectoryChange;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lvcs.impl.statistics.LocalHistoryCounter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DirectoryHistoryDialogModel extends HistoryDialogModel {
  public DirectoryHistoryDialogModel(Project p, IdeaGateway gw, LocalHistoryFacade vcs, VirtualFile f) {
    super(p, gw, vcs, f);
  }

  @ApiStatus.Internal
  @Override
  protected DirectoryChange createChange(@NotNull Difference d) {
    return new DirectoryChange(myGateway, d);
  }

  @Override
  public Reverter createReverter() {
    return createRevisionReverter(getDifferences());
  }

  public Reverter createRevisionReverter(List<Difference> diffs) {
    return new DifferenceReverter(myProject, myVcs, myGateway, diffs, getLeftRevision());
  }

  @ApiStatus.Internal
  @Override
  public @NotNull LocalHistoryCounter.Kind getKind() {
    return LocalHistoryCounter.Kind.Directory;
  }
}
