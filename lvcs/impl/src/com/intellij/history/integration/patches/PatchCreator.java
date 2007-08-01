package com.intellij.history.integration.patches;

import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.ui.models.DirectoryDifferenceModel;
import com.intellij.history.integration.ui.views.DirectoryDifference;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchBuilder;
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class PatchCreator {
  public static void create(IdeaGateway gw, Revision left, Revision right, String filePath, boolean isReverse)
    throws IOException, VcsException {
    List<Change> changes = getChangesBetween(left, right);

    Writer writer = new OutputStreamWriter(new FileOutputStream(filePath));
    try {
      List<FilePatch> patches = PatchBuilder.buildPatch(changes, gw.getBaseDir(), false, isReverse);
      UnifiedDiffWriter.write(patches, writer);
    }
    finally {
      writer.close();
    }
  }

  private static List<Change> getChangesBetween(Revision left, Revision right) {
    // todo temporary HACK!!!
    Difference d = left.getDifferenceWith(right);
    DirectoryDifferenceModel m = new DirectoryDifferenceModel(d);

    List<Change> changes = new ArrayList<Change>();
    flatternChanges(m, changes);
    return changes;
  }

  private static void flatternChanges(DirectoryDifferenceModel m, List<Change> changes) {
    if (!m.getDifferenceKind().equals(Difference.Kind.NOT_MODIFIED)) {
      changes.add(new DirectoryDifference(m));
    }
    for (DirectoryDifferenceModel child : m.getChildren()) {
      flatternChanges(child, changes);
    }
  }
}
