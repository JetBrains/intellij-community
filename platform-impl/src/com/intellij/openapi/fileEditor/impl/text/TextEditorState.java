package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;

/**
 * @author Vladimir Kondratyev
 */
final class TextEditorState implements FileEditorState {

  public int LINE;
  public int COLUMN;
  public float VERTICAL_SCROLL_PROPORTION;
  public int SELECTION_START;
  public int SELECTION_END;
  /**
   * State which describes how editor is folded.
   * This field can be <code>null</code>.
   */
  public CodeFoldingState FOLDING_STATE;

  private static final int MIN_CHANGE_DISTANCE = 4;

  public TextEditorState() {
  }

  public boolean equals(Object o) {
    if (!(o instanceof TextEditorState)) {
      return false;
    }

    final TextEditorState textEditorState = (TextEditorState)o;

    if (COLUMN != textEditorState.COLUMN) return false;
    if (LINE != textEditorState.LINE) return false;
    if (VERTICAL_SCROLL_PROPORTION != textEditorState.VERTICAL_SCROLL_PROPORTION) return false;
    if (SELECTION_START != textEditorState.SELECTION_START) return false;
    if (SELECTION_END != textEditorState.SELECTION_END) return false;

    return true;
  }

  public int hashCode() {
    return LINE + COLUMN;
  }

  public boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level) {
    if (!(otherState instanceof TextEditorState)) return false;
    TextEditorState other = (TextEditorState)otherState;
    return level == FileEditorStateLevel.NAVIGATION && Math.abs(LINE - other.LINE) < MIN_CHANGE_DISTANCE;
  }

  public String toString() {
    return "[" + LINE + "," + COLUMN + "]";
  }

}
