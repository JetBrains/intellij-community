// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration.revertion;

import com.intellij.diff.Block;
import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.ui.models.Progress;
import com.intellij.history.integration.ui.models.SelectionCalculator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.diff.FilesTooBigForDiffException;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class SelectionReverter extends Reverter {
  private final SelectionCalculator myCalculator;
  private final Revision myLeftRevision;
  private final Entry myRightEntry;
  private final int myFromLine;
  private final int myToLine;

  public SelectionReverter(Project p,
                           LocalHistoryFacade vcs,
                           IdeaGateway gw,
                           SelectionCalculator c,
                           Revision leftRevision,
                           Entry rightEntry,
                           int fromLine,
                           int toLine) {
    super(p, vcs, gw);
    myCalculator = c;
    myLeftRevision = leftRevision;
    myRightEntry = rightEntry;
    myFromLine = fromLine;
    myToLine = toLine;
  }

  @Override
  protected Revision getTargetRevision() {
    return myLeftRevision;
  }

  @Override
  protected List<VirtualFile> getFilesToClearROStatus() throws IOException {
    VirtualFile file = myGateway.findVirtualFile(myRightEntry.getPath());
    return Collections.singletonList(file);
  }

  @Override
  protected void doRevert() throws IOException, FilesTooBigForDiffException {
    Block b = myCalculator.getSelectionFor(myLeftRevision, new Progress() {
      @Override
      public void processed(int percentage) {
        // should be already processed.
      }
    });

    Document d = myGateway.getDocument(myRightEntry.getPath());

    int from = d.getLineStartOffset(myFromLine);
    int to = d.getLineEndOffset(myToLine);

    d.replaceString(from, to, b.getBlockContent());
  }
}
