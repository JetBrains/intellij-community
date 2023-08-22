// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.integration;

import com.intellij.history.core.LabelImpl;
import com.intellij.history.core.revisions.ChangeRevision;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.ui.models.HistoryDialogModel;
import com.intellij.history.integration.ui.models.RevisionItem;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class LocalHistoryUtil {

  static int findRevisionIndexToRevert(@NotNull HistoryDialogModel dirHistoryModel, @NotNull LabelImpl label) {
    List<RevisionItem> revs = dirHistoryModel.getRevisions();
    for (int i = 0; i < revs.size(); i++) {
      final RevisionItem rev = revs.get(i);
      if (isLabelRevision(rev, label)) return i;
      //when lvcs model is not constructed yet or is empty then PutLabelChange is created but without label, so we need to scan revisions themselves
      if (isChangeWithId(rev.revision, label.getLabelChangeId())) return i;
    }
    return -1;
  }

  static boolean isLabelRevision(@NotNull RevisionItem rev, @NotNull LabelImpl label) {
    final long targetChangeId = label.getLabelChangeId();
    return ContainerUtil.exists(rev.labels, revision -> isChangeWithId(revision, targetChangeId));
  }

  private static boolean isChangeWithId(@NotNull Revision revision, long targetChangeId) {
    return revision instanceof ChangeRevision && ((ChangeRevision)revision).containsChangeWithId(targetChangeId);
  }
}
