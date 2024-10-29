// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.history.integration.ui.views;

import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class DirectoryChange extends Change {
  private final @NotNull IdeaGateway myGateway;
  private final @NotNull Difference myDifference;

  public DirectoryChange(@NotNull IdeaGateway gateway, @NotNull Difference difference) {
    super(difference.getLeftContentRevision(gateway), difference.getRightContentRevision(gateway));
    myGateway = gateway;
    myDifference = difference;
  }

  public boolean canShowFileDifference() {
    if (!myDifference.isFile()) return false;
    Entry e = getLeftEntry();
    if (e == null) e = getRightEntry();
    return myGateway.areContentChangesVersioned(e.getName());
  }

  public @NotNull Difference getDifference() {
    return myDifference;
  }

  public @Nullable Entry getLeftEntry() {
    return myDifference.getLeft();
  }

  public @Nullable Entry getRightEntry() {
    return myDifference.getRight();
  }
}
