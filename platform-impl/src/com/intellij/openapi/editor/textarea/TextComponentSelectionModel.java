package com.intellij.openapi.editor.textarea;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.event.SelectionListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.text.JTextComponent;

/**
 * @author yole
 */
public class TextComponentSelectionModel implements SelectionModel {
  private JTextComponent myTextComponent;
  private TextComponentEditor myEditor;

  public TextComponentSelectionModel(final JTextComponent textComponent, final TextComponentEditor textComponentEditor) {
    myTextComponent = textComponent;
    myEditor = textComponentEditor;
  }

  public int getSelectionStart() {
    return myTextComponent.getSelectionStart();
  }

  public int getSelectionEnd() {
    return myTextComponent.getSelectionEnd();
  }

  @Nullable
  public String getSelectedText() {
    return myTextComponent.getSelectedText();
  }

  public int getLeadSelectionOffset() {
    final int caretPosition = myTextComponent.getCaretPosition();
    final int start = myTextComponent.getSelectionStart();
    final int end = myTextComponent.getSelectionEnd();
    return caretPosition == start ? end : start;
  }

  public boolean hasSelection() {
    return myTextComponent.getSelectionStart() != myTextComponent.getSelectionEnd();
  }

  public void setSelection(final int startOffset, final int endOffset) {
    if (myTextComponent.getCaretPosition() == startOffset) {   // avoid moving caret (required for correct Ctrl-W operation)
      myTextComponent.setCaretPosition(endOffset);
      myTextComponent.moveCaretPosition(startOffset);
    }
    else {
      myTextComponent.setCaretPosition(startOffset);
      myTextComponent.moveCaretPosition(endOffset);
    }
  }

  public void removeSelection() {
    final int position = myTextComponent.getCaretPosition();
    myTextComponent.select(position, position);
  }

  public void addSelectionListener(final SelectionListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void removeSelectionListener(final SelectionListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void selectLineAtCaret() {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void selectWordAtCaret(final boolean honorCamelWordsSettings) {
    removeSelection();

    EditorActionHandler handler = EditorActionManager.getInstance().getActionHandler(
      IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
    handler.execute(myEditor, DataManager.getInstance().getDataContext(myEditor.getComponent()));
  }

  public void copySelectionToClipboard() {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void setBlockSelection(final LogicalPosition blockStart, final LogicalPosition blockEnd) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void removeBlockSelection() {
  }

  public boolean hasBlockSelection() {
    return false;
  }

  @NotNull
  public int[] getBlockSelectionStarts() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @NotNull
  public int[] getBlockSelectionEnds() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Nullable
  public LogicalPosition getBlockStart() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Nullable
  public LogicalPosition getBlockEnd() {
    throw new UnsupportedOperationException("Not implemented");
  }

  public boolean isBlockSelectionGuarded() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Nullable
  public RangeMarker getBlockSelectionGuard() {
    throw new UnsupportedOperationException("Not implemented");
  }
}