/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.history.integration;

import com.intellij.history.core.LabelImpl;
import com.intellij.history.core.revisions.ChangeRevision;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.ui.models.HistoryDialogModel;
import com.intellij.history.integration.ui.models.RevisionItem;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class LocalHistoryUtil {

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
    return ContainerUtil.exists(rev.labels, new Condition<Revision>() {
      @Override
      public boolean value(Revision revision) {
        return isChangeWithId(revision, targetChangeId);
      }
    });
  }

  private static boolean isChangeWithId(@NotNull Revision revision, long targetChangeId) {
    return revision instanceof ChangeRevision && ((ChangeRevision)revision).containsChangeWithId(targetChangeId);
  }
}
