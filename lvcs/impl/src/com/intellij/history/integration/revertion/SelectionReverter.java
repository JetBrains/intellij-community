package com.intellij.history.integration.revertion;

import com.intellij.history.core.ILocalVcs;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.FormatUtil;
import com.intellij.history.integration.LocalHistoryBundle;
import com.intellij.history.integration.ui.models.SelectionCalculator;
import com.intellij.history.integration.ui.models.Progress;
import com.intellij.openapi.editor.Document;
import com.intellij.diff.Block;

import java.io.IOException;

public class SelectionReverter extends Reverter {
  private SelectionCalculator myCalculator;
  private Revision myLeftRevision;
  private Entry myRightEntry;
  private int myFromLine;
  private int myToLine;

  public SelectionReverter(ILocalVcs vcs,
                           IdeaGateway gw,
                           SelectionCalculator c,
                           Revision leftRevision,
                           Entry rightEntry,
                           int fromLine,
                           int toLine) {
    super(vcs, gw);
    myCalculator = c;
    myLeftRevision = leftRevision;
    myRightEntry = rightEntry;
    myFromLine = fromLine;
    myToLine = toLine;
  }

  protected String formatCommandName() {
    String date = FormatUtil.formatTimestamp(myLeftRevision.getTimestamp());
    return LocalHistoryBundle.message("system.label.revert.of.selection.to.date", date);
  }

  protected void doRevert() throws IOException {
    Block b = myCalculator.getSelectionFor(myLeftRevision, new Progress() {
      public void processed(int percentage) {
        // should be already processed.
      }
    });

    Document d = myGateway.getDocumentFor(myRightEntry.getPath());

    int from = d.getLineStartOffset(myFromLine);
    int to = d.getLineEndOffset(myToLine);

    d.replaceString(from, to, b.getBlockContent());
  }
}
