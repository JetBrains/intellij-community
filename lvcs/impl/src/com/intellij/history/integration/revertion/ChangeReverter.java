package com.intellij.history.integration.revertion;

import com.intellij.history.core.ILocalVcs;
import com.intellij.history.core.changes.*;
import com.intellij.history.integration.FormatUtil;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryBundle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ChangeReverter extends Reverter {
  private ILocalVcs myVcs;
  private final IdeaGateway myGateway;
  private Change myChange;
  private List<Change> myChainCache;

  public ChangeReverter(ILocalVcs vcs, IdeaGateway gw, Change c) {
    super(vcs, gw);
    myVcs = vcs;
    myGateway = gw;
    myChange = c;
  }

  @Override
  public String askUserForProceed() throws IOException {
    final String[] result = new String[1];

    myVcs.accept(new ChangeVisitor() {
      @Override
      public void begin(ChangeSet c) throws StopVisitingException {
        if (isBeforeMyChange(c, false)) stop();
        if (!isInTheChain(c)) return;

        result[0] = LocalHistoryBundle.message("revert.message.should.revert.sequential.changes");
        stop();
      }
    });

    return result[0];
  }

  @Override
  protected void doCheckCanRevert(final List<String> errors) throws IOException {
    super.doCheckCanRevert(errors);

    myVcs.accept(selective(new ChangeVisitor() {
      @Override
      public void visit(StructuralChange c) {
        if (!c.canRevertOn(myRoot)) {
          errors.add(LocalHistoryBundle.message("revert.error.files.already.exist"));
          return;
        }
        c.revertOn(myRoot);
      }
    }));
  }

  @Override
  protected String formatCommandName() {
    String name = myChange.getName();
    if (name != null) {
      return LocalHistoryBundle.message("system.label.revert.of.change", name);
    }

    String date = FormatUtil.formatTimestamp(myChange.getTimestamp());
    return LocalHistoryBundle.message("system.label.revert.of.change.made.at.date", date);
  }

  @Override
  protected void doRevert() throws IOException {
    myVcs.accept(selective(new ChangeRevertionVisitor(myGateway)));
  }

  @Override
  protected ChangeVisitor selective(ChangeVisitor v) {
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
    if (myChainCache == null) {
      myChainCache = myVcs.getChain(myChange);
    }
    return c.affectsSameAs(myChainCache);
  }
}
