package com.intellij.openapi.fileEditor;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.LogicalPosition;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 21, 2004
 * Time: 5:15:34 PM
 * To change this template use File | Settings | File Templates.
 */
public class TextEditorLocation implements FileEditorLocation {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.TextEditorLocation");

  private final TextEditor myEditor;
  private final LogicalPosition myPosition;

  public TextEditorLocation(int offset, TextEditor editor) {
    this(editor.getEditor().offsetToLogicalPosition(offset), editor);
  }

  public TextEditorLocation(LogicalPosition position, TextEditor editor) {
    myEditor = editor;
    myPosition = position;
  }


  public FileEditor getEditor() {
    return myEditor;
  }

  public LogicalPosition getPosition() {
    return myPosition;
  }

  public int compareTo(FileEditorLocation fileEditorLocation) {
    TextEditorLocation otherLocation = (TextEditorLocation)fileEditorLocation;
    LOG.assertTrue(myEditor == otherLocation.myEditor);

    return myPosition.compareTo(otherLocation.myPosition);
  }

  public String toString() {
    return myPosition.toString();
  }
}
