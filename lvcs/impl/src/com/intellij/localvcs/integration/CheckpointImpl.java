package com.intellij.localvcs.integration;

import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.core.changes.Change;
import com.intellij.localvcs.core.changes.ChangeVisitor;

import java.util.List;

public class CheckpointImpl implements Checkpoint {
  private Change myLastGlobalChange;
  private IdeaGateway myGateway;
  private ILocalVcs myVcs;

  public CheckpointImpl(IdeaGateway gw, ILocalVcs vcs) {
    myGateway = gw;
    myVcs = vcs;
    myLastGlobalChange = myVcs.getLastGlobalChange();
  }

  public void revertToPreviousState() {
    doRevert(true);
  }

  public void revertToThatState() {
    doRevert(false);
  }

  private void doRevert(boolean revertLastChange) {
    try {
      List<Change> cc = myVcs.getChangesAfter(myLastGlobalChange);

      ChangeVisitor v = new ChangeRevertionVisitor(myVcs, myGateway);
      for (Change c : cc) c.accept(v);

      if (revertLastChange) myLastGlobalChange.accept(v);

    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
