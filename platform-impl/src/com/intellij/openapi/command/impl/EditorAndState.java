package com.intellij.openapi.command.impl;

import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditor;

import java.lang.ref.WeakReference;

class EditorAndState {
  private final FileEditorState myState;
  private final WeakReference<FileEditor> myEditor;

  public EditorAndState(FileEditor editor, FileEditorState state) {
    myEditor = new WeakReference<FileEditor>(editor);
    myState = state;
  }

  /**
   * @return may be null
   */ 
  public FileEditor getEditor() {
    return myEditor.get();
  }

  public FileEditorState getState() {
    return myState;
  }
}
