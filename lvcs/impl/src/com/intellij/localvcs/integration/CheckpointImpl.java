package com.intellij.localvcs.integration;

import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.core.changes.Change;
import com.intellij.localvcs.core.changes.ChangeFileContentChange;
import com.intellij.localvcs.core.changes.ChangeSet;
import com.intellij.localvcs.core.changes.ChangeVisitor;
import com.intellij.localvcs.integration.revert.ChangeRevertionVisitor;

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

  public void revertToPreviousState() {
    doRevert(true);
  }

  public void revertToThatState() {
    doRevert(false);
  }

  private void doRevert(boolean revertLastChange) {
    try {
      ChangeVisitor v = new GlobalChangesRevertionVisitor(myVcs, myGateway);
      myVcs.accept(new SelectiveChangeVisitor(v, revertLastChange));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private class GlobalChangesRevertionVisitor extends ChangeRevertionVisitor {
    public GlobalChangesRevertionVisitor(ILocalVcs vcs, IdeaGateway gw) {
      super(vcs, gw);
    }

    @Override
    protected boolean shouldProcess(Change c) {
      return !(c instanceof ChangeFileContentChange);
    }
  }

  private class SelectiveChangeVisitor extends ChangeVisitor {
    private ChangeVisitor myVisitor;
    private boolean myRevertLastChange;

    public SelectiveChangeVisitor(ChangeVisitor v, boolean revertLastChange) {
      myVisitor = v;
      myRevertLastChange = revertLastChange;
    }

    @Override
    public void visit(ChangeSet c) {
    }

    @Override
    public void visit(Change c) throws IOException, StopVisitingException {
      if (c == myLastChange) {
        if (myRevertLastChange) c.accept(myVisitor);
        stop();
      }
      c.accept(myVisitor);
    }
  }
}
