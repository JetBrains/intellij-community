// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.history.integration.ui.models;

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.history.integration.revertion.SelectionReverter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lvcs.impl.statistics.LocalHistoryCounter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@ApiStatus.Internal
public class SelectionHistoryDialogModel extends FileHistoryDialogModel {
  private RevisionSelectionCalculator myCalculatorCache;
  private final int myFrom;
  private final int myTo;

  public SelectionHistoryDialogModel(Project p, IdeaGateway gw, LocalHistoryFacade vcs, VirtualFile f, int from, int to) {
    super(p, gw, vcs, f);
    myFrom = from;
    myTo = to;
  }

  @Override
  protected @NotNull RevisionData collectRevisionData() {
    myCalculatorCache = null;
    return super.collectRevisionData();
  }

  @Override
  public FileDifferenceModel getDifferenceModel() {
    return new SelectionDifferenceModel(myProject,
                                        myGateway,
                                        getCalculator(),
                                        getLeftRevision(),
                                        getRightRevision(),
                                        myFrom,
                                        myTo,
                                        isCurrentRevisionSelected());
  }

  private RevisionSelectionCalculator getCalculator() {
    if (myCalculatorCache == null) {
      myCalculatorCache = new RevisionSelectionCalculator(myGateway, RevisionDataKt.getAllRevisions(getRevisionData()), myFrom, myTo);
    }
    return myCalculatorCache;
  }

  @Override
  public Reverter createReverter() {
    return new SelectionReverter(myProject, myVcs, myGateway, getCalculator(), getLeftRevision(), getRightEntry(), myFrom, myTo);
  }

  @Override
  public @NotNull Set<Long> filterContents(@NotNull String filter) {
    return RevisionDataKt.filterContents(getCalculator(), filter);
  }

  @ApiStatus.Internal
  @Override
  public @NotNull LocalHistoryCounter.Kind getKind() {
    return LocalHistoryCounter.Kind.Selection;
  }
}
