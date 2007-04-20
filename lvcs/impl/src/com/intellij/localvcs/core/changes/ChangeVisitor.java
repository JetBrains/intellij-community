package com.intellij.localvcs.core.changes;

public interface ChangeVisitor {
  void visit(CreateFileChange c) throws Exception;

  void visit(CreateDirectoryChange c) throws Exception;

  void visit(ChangeFileContentChange c) throws Exception;

  void visit(RenameChange c) throws Exception;

  void visit(MoveChange c) throws Exception;

  void visit(DeleteChange c) throws Exception;
}
