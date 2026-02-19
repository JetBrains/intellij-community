// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.history.integration.ui.models;

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.revertion.DifferenceReverter;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lvcs.impl.statistics.LocalHistoryCounter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public abstract class FileHistoryDialogModel extends HistoryDialogModel {
  public FileHistoryDialogModel(Project p, IdeaGateway gw, LocalHistoryFacade vcs, VirtualFile f) {
    super(p, gw, vcs, f);
  }

  public abstract FileDifferenceModel getDifferenceModel();

  @Override
  public Reverter createReverter() {
    Revision l = getLeftRevision();
    Revision r = getRightRevision();
    return new DifferenceReverter(myProject, myVcs, myGateway, Revision.getDifferencesBetween(l, r), l);
  }

  public @NotNull Set<Long> filterContents(@NotNull String filter) {
    return RevisionDataKt.filterContents(myVcs, myGateway, myFile, ContainerUtil.map(getRevisions(), item -> item.revision), filter,
                                         myBefore);
  }

  @ApiStatus.Internal
  @Override
  public @NotNull LocalHistoryCounter.Kind getKind() {
    return LocalHistoryCounter.Kind.File;
  }
}
