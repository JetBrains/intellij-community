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

import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryBundle;
import com.intellij.history.integration.revertion.ChangeReverter;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.history.integration.revertion.SelectionReverter;
import com.intellij.history.integration.ui.models.*;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.List;

public class SelectionHistoryDialogModel extends FileHistoryDialogModel {
  private SelectionCalculator myCalculatorCache;
  private final int myFrom;
  private final int myTo;

  public SelectionHistoryDialogModel(IdeaGateway gw, LocalVcs vcs, VirtualFile f, int from, int to) {
    super(gw, vcs, f);
    myFrom = from;
    myTo = to;
  }

  @Override
  protected List<Revision> getRevisionsCache() {
    myCalculatorCache = null;
    return super.getRevisionsCache();
  }

  @Override
  public FileDifferenceModel getDifferenceModel() {
    return new SelectionDifferenceModel(myGateway,
                                        getCalculator(),
                                        getLeftRevision(),
                                        getRightRevision(),
                                        myFrom,
                                        myTo,
                                        isCurrentRevisionSelected());
  }

  private SelectionCalculator getCalculator() {
    if (myCalculatorCache == null) {
      myCalculatorCache = new SelectionCalculator(myGateway, getRevisions(), myFrom, myTo);
    }
    return myCalculatorCache;
  }

  @Override
  protected Reverter createRevisionReverter() {
    return new SelectionReverter(myVcs, myGateway, getCalculator(), getLeftRevision(), getRightEntry(), myFrom, myTo);
  }

  @Override
  protected ChangeReverter createChangeReverter() {
    return new ChangeReverter(myVcs, myGateway, getRightRevision().getCauseChange()) {
      @Override
      public List<String> askUserForProceeding() throws IOException {
        List<String> result = super.askUserForProceeding();
        result.add(LocalHistoryBundle.message("revert.message.will.revert.whole.file"));
        return result;
      }
    };
  }
}
