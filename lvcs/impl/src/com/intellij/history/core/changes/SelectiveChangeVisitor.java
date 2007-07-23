package com.intellij.history.core.changes;

import com.intellij.history.core.tree.Entry;

import java.io.IOException;

public abstract class SelectiveChangeVisitor extends ChangeVisitor {
  private ChangeVisitor myVisitor;

  public SelectiveChangeVisitor(ChangeVisitor v) {
    myVisitor = v;
  }

  @Override
  public void begin(ChangeSet c) throws StopVisitingException, IOException {
    if (isFinished(c)) stop();
  }

  protected abstract boolean isFinished(ChangeSet c);

  @Override
  public void visit(StructuralChange c) throws IOException, StopVisitingException {
    if (!shouldProcess(c)) return;
    c.accept(myVisitor);
  }

  protected abstract boolean shouldProcess(StructuralChange c);

  @Override
  public void started(Entry r) throws IOException {
    myVisitor.started(r);
  }

  @Override
  public void finished() throws IOException {
    myVisitor.finished();
  }
}
