package com.intellij.history.core.changes;

import java.io.IOException;

public abstract class ChangeVisitor {
  public void begin(ChangeSet c) throws IOException, StopVisitingException {
  }

  public void end(ChangeSet c) throws IOException, StopVisitingException {

  }

  public void visit(PutLabelChange c) throws IOException, StopVisitingException {
  }

  public void visit(StructuralChange c) throws IOException, StopVisitingException {
  }

  public void visit(CreateEntryChange c) throws IOException, StopVisitingException {
    visit((StructuralChange)c);
  }

  public void visit(ChangeFileContentChange c) throws IOException, StopVisitingException {
    visit((StructuralChange)c);
  }

  public void visit(RenameChange c) throws IOException, StopVisitingException {
    visit((StructuralChange)c);
  }

  public void visit(MoveChange c) throws IOException, StopVisitingException {
    visit((StructuralChange)c);
  }

  public void visit(DeleteChange c) throws IOException, StopVisitingException {
    visit((StructuralChange)c);
  }

  public void finished() throws IOException {
  }

  protected void stop() throws StopVisitingException {
    throw new StopVisitingException();
  }

  public static class StopVisitingException extends Exception {
  }
}
