// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.history.integration.ui.models;

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

@ApiStatus.Internal
public final class RecentChangeDialogModel extends DirectoryHistoryDialogModel {
  private final RecentChange myChange;

  public RecentChangeDialogModel(Project p, IdeaGateway gw, LocalHistoryFacade vcs, RecentChange c) {
    super(p, gw, vcs, null);
    myChange = c;
    resetSelection();
  }

  @Override
  protected @NotNull RevisionData collectRevisionData() {
    return new RevisionData(myChange.getRevisionAfter(),
                            Collections.singletonList(new RevisionItem(myChange.getRevisionBefore())));
  }

  @Override
  public String getTitle() {
    return myChange.getChangeName();
  }
}
