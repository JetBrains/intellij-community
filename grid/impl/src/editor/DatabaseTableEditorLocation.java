package com.intellij.database.editor;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import org.jetbrains.annotations.NotNull;

/**
* @author Gregory.Shrago
*/
class DatabaseTableEditorLocation implements FileEditorLocation {
  private final FileEditor myEditor;
  public final int myRow;
  public final int myColumn;


  DatabaseTableEditorLocation(@NotNull FileEditor editor, int row, int column) {
    myEditor = editor;
    myRow = row;
    myColumn = column;
  }

  @Override
  public @NotNull FileEditor getEditor() {
    return myEditor;
  }

  public int getRow() {
    return myRow;
  }

  public int getColumn() {
    return myColumn;
  }

  @Override
  public int compareTo(final FileEditorLocation location) {
    if (!(location instanceof DatabaseTableEditorLocation that)) return 0;
    if (myRow != that.myRow) return myRow - that.myRow;
    if (myColumn != that.myColumn) return myColumn - that.myColumn;
    return 0;
  }
}
