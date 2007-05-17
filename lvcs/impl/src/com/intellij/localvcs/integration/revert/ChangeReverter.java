package com.intellij.localvcs.integration.revert;

import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.core.changes.Change;
import com.intellij.localvcs.core.changes.ChangeSet;
import com.intellij.localvcs.integration.IdeaGateway;

import java.io.IOException;

public class ChangeReverter {
  private ILocalVcs myVcs;
  private IdeaGateway myGateway;
  private Change myChange;

  public ChangeReverter(ILocalVcs vcs, IdeaGateway gw, Change c) {

    myVcs = vcs;
    myGateway = gw;
    myChange = c;
  }

  // todo refactor myChangeList.isInTheChain and test it

  public void revert() throws IOException {
    myVcs.accept(new ChangeRevertionVisitor(myVcs, myGateway) {
      @Override
      public void visit(ChangeSet c) throws StopVisitingException {
        if (!myVcs.isBefore(myChange, c, true)) stop();
      }

      protected boolean shouldProcess(Change c) {
        return myVcs.isInTheChain(myChange, c);
      }
    });
  }
}
