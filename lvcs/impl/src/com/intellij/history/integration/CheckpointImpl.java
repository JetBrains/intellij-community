package com.intellij.history.integration;

import com.intellij.history.core.ILocalVcs;
import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.ChangeFileContentChange;
import com.intellij.history.core.changes.ChangeVisitor;
import com.intellij.history.core.changes.StructuralChange;
import com.intellij.history.integration.revertion.ChangeRevertionVisitor;
import com.intellij.history.Checkpoint;

import java.io.IOException;

public class CheckpointImpl implements Checkpoint {
  private Change myLastChange;
  private IdeaGateway myGateway;
  private ILocalVcs myVcs;

  public CheckpointImpl(IdeaGateway gw, ILocalVcs vcs) {
    myGateway = gw;
    myVcs = vcs;
    myLastChange = myVcs.getLastChange();
  }

  public void revertToPreviousState() throws IOException {
    doRevert(true);
  }

  public void revertToThatState() throws IOException {
    doRevert(false);
  }

  private void doRevert(boolean revertLastChange) throws IOException {
    ChangeVisitor v = new ChangeRevertionVisitor(myVcs, myGateway);
    myVcs.accept(new SelectiveChangeVisitor(v, revertLastChange));
  }

  private class SelectiveChangeVisitor extends ChangeVisitor {
    private ChangeVisitor myVisitor;
    private boolean myRevertLastChange;

    public SelectiveChangeVisitor(ChangeVisitor v, boolean revertLastChange) {
      myVisitor = v;
      myRevertLastChange = revertLastChange;
    }

    @Override
    public void visit(StructuralChange c) throws IOException, StopVisitingException {
      if (c == myLastChange) {
        if (myRevertLastChange) doVisit(c);
        stop();
      }
      doVisit(c);
    }

    private void doVisit(StructuralChange c) throws IOException, StopVisitingException {
      if (c instanceof ChangeFileContentChange) return;
      c.accept(myVisitor);
    }

    @Override
    public void finished() throws IOException {
      myVisitor.finished();
    }
  }
}
