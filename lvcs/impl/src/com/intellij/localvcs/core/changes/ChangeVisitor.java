package com.intellij.localvcs.core.changes;

import java.io.IOException;

public abstract class ChangeVisitor {
  public void visit(ChangeSet c) throws IOException, StopVisitingException {
  }

  public void visit(CreateFileChange c) throws IOException, StopVisitingException {
  }

  public void visit(CreateDirectoryChange c) throws IOException, StopVisitingException {
  }

  public void visit(ChangeFileContentChange c) throws IOException, StopVisitingException {
  }

  public void visit(RenameChange c) throws IOException, StopVisitingException {
  }

  public void visit(MoveChange c) throws IOException, StopVisitingException {
  }

  public void visit(DeleteChange c) throws IOException, StopVisitingException {
  }

  protected void stop() throws StopVisitingException {
    throw new StopVisitingException();
  }

  public static class StopVisitingException extends Exception {
  }
}
