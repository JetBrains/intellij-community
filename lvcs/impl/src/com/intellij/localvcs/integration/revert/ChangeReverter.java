package com.intellij.localvcs.integration.revert;

import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.core.changes.Change;
import com.intellij.localvcs.core.changes.ChangeSet;
import com.intellij.localvcs.core.changes.RenameChange;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.integration.IdeaGateway;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class ChangeReverter extends Reverter {
  private ILocalVcs myVcs;
  private IdeaGateway myGateway;
  private Change myChange;

  public ChangeReverter(ILocalVcs vcs, IdeaGateway gw, Change c) {

    myVcs = vcs;
    myGateway = gw;
    myChange = c;
  }

  @Override
  public List<String> checkCanRevert() throws IOException {
    final boolean[] result = new boolean[]{true};

    myVcs.accept(new ChangeRevertionVisitor(myVcs, myGateway) {
      @Override
      public void visit(ChangeSet c) throws StopVisitingException {
        if (!myVcs.isBefore(myChange, c, true)) stop();
      }

      @Override
      public void visit(RenameChange c) throws IOException, StopVisitingException {
        if (shouldProcess(c)) {
          Entry e = getAffectedEntry(c);
          if (e.getParent().findChild(c.getOldName()) != null) {
            result[0] = false;
            stop();
          }
        }
        super.visit(c);
      }

      protected boolean shouldProcess(Change c) {
        return myVcs.isInTheChain(myChange, c);
      }
    });

    return Collections.singletonList("some files already exist");
  }

  // todo refactor myChangeList.isInTheChain and test it

  @Override
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
