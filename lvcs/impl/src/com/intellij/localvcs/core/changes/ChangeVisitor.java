package com.intellij.localvcs.core.changes;

public abstract class ChangeVisitor {
  public void visit(ChangeSet c) throws Exception {
  }

  public void visit(CreateFileChange c) throws Exception {
  }

  public void visit(CreateDirectoryChange c) throws Exception {
  }

  public void visit(ChangeFileContentChange c) throws Exception {
  }

  public void visit(RenameChange c) throws Exception {
  }

  public void visit(MoveChange c) throws Exception {
  }

  public void visit(DeleteChange c) throws Exception {
  }

  protected void stop() throws StopVisitorException {
    throw new StopVisitorException();
  }

  public static class StopVisitorException extends Exception {
  }
}
