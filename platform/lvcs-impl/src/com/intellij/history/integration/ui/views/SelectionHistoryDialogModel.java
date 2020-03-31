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

package com.intellij.history.integration.ui.views;

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.history.integration.revertion.SelectionReverter;
import com.intellij.history.integration.ui.models.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;

import java.util.ArrayList;
import java.util.List;

public class SelectionHistoryDialogModel extends FileHistoryDialogModel {
  private SelectionCalculator myCalculatorCache;
  private final int myFrom;
  private final int myTo;

  public SelectionHistoryDialogModel(Project p, IdeaGateway gw, LocalHistoryFacade vcs, VirtualFile f, int from, int to) {
    super(p, gw, vcs, f);
    myFrom = from;
    myTo = to;
  }

  @Override
  protected Pair<Revision, List<RevisionItem>> calcRevisionsCache() {
    myCalculatorCache = null;
    return super.calcRevisionsCache();
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

  private SelectionCalculator getCalculator() {
    if (myCalculatorCache == null) {
      List<Revision> revisionList = new ArrayList<>();
      revisionList.add(getCurrentRevision());
      
      revisionList.addAll(ContainerUtil.map(getRevisions(), revisionItem -> revisionItem.revision));
      myCalculatorCache = new SelectionCalculator(myGateway, revisionList, myFrom, myTo);
    }
    return myCalculatorCache;
  }

  @Override
  public Reverter createReverter() {
    return new SelectionReverter(myProject, myVcs, myGateway, getCalculator(), getLeftRevision(), getRightEntry(), myFrom, myTo);
  }
}
