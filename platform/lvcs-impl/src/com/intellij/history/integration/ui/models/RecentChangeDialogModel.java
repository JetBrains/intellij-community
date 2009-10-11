/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.history.integration.ui.models;

import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.revisions.RecentChange;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.IdeaGateway;

import java.util.ArrayList;
import java.util.List;

public class RecentChangeDialogModel extends DirectoryHistoryDialogModel {
  private final RecentChange myChange;

  public RecentChangeDialogModel(IdeaGateway gw, LocalVcs vcs, RecentChange c) {
    super(gw, vcs, null);
    myChange = c;
    resetSelection();
  }

  @Override
  protected List<Revision> getRevisionsCache() {
    final List<Revision> result = new ArrayList<Revision>();
    result.add(myChange.getRevisionAfter());
    result.add(myChange.getRevisionBefore());
    return result;
  }

  @Override
  public String getTitle() {
    return myChange.getChangeName();
  }

  @Override
  protected void resetSelection() {
    selectChanges(0, 0);
  }
}
