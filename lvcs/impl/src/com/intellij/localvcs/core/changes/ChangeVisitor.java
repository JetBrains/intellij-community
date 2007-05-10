package com.intellij.localvcs.core.changes;

public abstract class ChangeVisitor {
  public abstract void visit(CreateFileChange c) throws Exception;

  public abstract void visit(CreateDirectoryChange c) throws Exception;

  public abstract void visit(ChangeFileContentChange c) throws Exception;

  public abstract void visit(RenameChange c) throws Exception;

  public abstract void visit(MoveChange c) throws Exception;

  public abstract void visit(DeleteChange c) throws Exception;

  protected void stop() throws StopVisitorException {
    throw new StopVisitorException();
  }

  public static class StopVisitorException extends Exception {
  }
}
