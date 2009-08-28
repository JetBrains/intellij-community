package com.intellij.psi.impl.cache.impl.todo;

import com.intellij.openapi.fileTypes.FileTypeExtension;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileContent;

/**
 * @author yole
 */
public class TodoIndexers extends FileTypeExtension<DataIndexer<TodoIndexEntry, Integer, FileContent>> {
  public static TodoIndexers INSTANCE = new TodoIndexers();

  private TodoIndexers() {
    super("com.intellij.todoIndexer");
  }
}
