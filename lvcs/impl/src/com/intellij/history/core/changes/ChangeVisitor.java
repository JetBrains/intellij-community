package com.intellij.history.core.changes;

import com.intellij.history.core.tree.Entry;

import java.io.IOException;

public abstract class ChangeVisitor {
  protected Entry myRoot;

  public void started(Entry root) throws IOException {
    myRoot = root;
  }

  public void finished() throws IOException {
  }

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

  protected void stop() throws StopVisitingException {
    throw new StopVisitingException();
  }

  public static class StopVisitingException extends Exception {
  }
}
