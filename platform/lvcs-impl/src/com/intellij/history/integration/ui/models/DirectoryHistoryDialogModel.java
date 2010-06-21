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

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.revertion.DifferenceReverter;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.history.integration.ui.views.DirectoryChange;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

public class DirectoryHistoryDialogModel extends HistoryDialogModel {
  public DirectoryHistoryDialogModel(Project p, IdeaGateway gw, LocalHistoryFacade vcs, VirtualFile f) {
    super(p, gw, vcs, f);
  }

  @Override
  protected DirectoryChange createChange(Difference d) {
    return new DirectoryChange(new DirectoryChangeModel(d, myGateway));
  }

  @Override
  public Reverter createReverter() {
    return createRevisionReverter(getDifferences());
  }

  public Reverter createRevisionReverter(List<Difference> diffs) {
    return new DifferenceReverter(myProject, myVcs, myGateway, diffs, getLeftRevision());
  }
}
