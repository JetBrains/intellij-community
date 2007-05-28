package com.intellij.localvcs.integration.revertion;

import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.core.IdPath;
import com.intellij.localvcs.core.changes.*;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.integration.FormatUtil;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChangeReverter extends Reverter {
  private ILocalVcs myVcs;
  private final IdeaGateway myGateway;
  private Change myChange;

  public ChangeReverter(ILocalVcs vcs, IdeaGateway gw, Change c) {
    super(gw);
    myVcs = vcs;
    myGateway = gw;
    myChange = c;
  }

  @Override
  public String askUserForProceed() throws IOException {
    final String[] result = new String[1];

    myVcs.accept(new ChangeVisitor() {
      @Override
      public void visit(ChangeSet c) throws StopVisitingException {
        if (isBeforeMyChange(c, false)) stop();
        if (!isInTheChain(c)) return;

        result[0] = "There are some changes that have been done after this one.\n" + "These changes should be reverted too.";
        stop();
      }
    });

    return result[0];
  }

  @Override
  public List<String> checkCanRevert() throws IOException {
    List<String> errors = new ArrayList<String>();
    if (!askForReadOnlyStatusClearing()) {
      errors.add("some files are read-only");
    }
    doCheckCanRevert(errors);
    return errors;
  }

  private boolean askForReadOnlyStatusClearing() throws IOException {
    return myGateway.ensureFilesAreWritable(getFilesToClearROStatus());
  }

  private ArrayList<VirtualFile> getFilesToClearROStatus() throws IOException {
    final Set<VirtualFile> files = new HashSet<VirtualFile>();

    myVcs.accept(selective(new ChangeVisitor() {
      @Override
      public void visit(StructuralChange c) {
        for (IdPath p : c.getAffectedIdPaths()) {
          Entry e = myVcs.getRootEntry().findEntry(p);
          if (e == null) continue;
          files.addAll(myGateway.getAllFilesFrom(e.getPath()));
        }
      }
    }));

    return new ArrayList<VirtualFile>(files);
  }

  private void doCheckCanRevert(final List<String> errors) throws IOException {
    final Entry r = myVcs.getRootEntry().copy();
    myVcs.accept(selective(new ChangeVisitor() {
      @Override
      public void visit(StructuralChange c) {
        if (!c.canRevertOn(r)) {
          errors.add("some files already exist");
          return;
        }
        c.revertOn(r);
      }
    }));
  }

  @Override
  protected String formatCommandName() {
    String name = myChange.getName();
    if (name != null) return "Revert '" + name + "'";

    String date = FormatUtil.formatTimestamp(myChange.getTimestamp());
    return "Revert change made " + date;
  }

  @Override
  protected void doRevert() throws IOException {
    myVcs.accept(selective(new ChangeRevertionVisitor(myVcs, myGateway)));
  }

  private ChangeVisitor selective(ChangeVisitor v) {
    return new SelectiveChangeVisitor(v) {
      @Override
      protected boolean isFinished(ChangeSet c) {
        return isBeforeMyChange(c, true);
      }

      @Override
      protected boolean shouldProcess(StructuralChange c) {
        return isInTheChain(c);
      }
    };
  }

  private boolean isBeforeMyChange(ChangeSet c, boolean canBeEqual) {
    return !myVcs.isBefore(myChange, c, canBeEqual);
  }

  private boolean isInTheChain(Change c) {
    return myVcs.isInTheChain(myChange, c);
  }
}
