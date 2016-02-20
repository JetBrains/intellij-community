/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.injected.editor;

import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
 */
public class SelectionModelWindow implements SelectionModel {
  private final SelectionModel myHostModel;
  private final DocumentWindow myDocument;
  private final EditorWindow myInjectedEditor;

  public SelectionModelWindow(final EditorEx delegate, final DocumentWindow document, EditorWindow injectedEditor) {
    myDocument = document;
    myInjectedEditor = injectedEditor;
    myHostModel = delegate.getSelectionModel();
  }

  @Override
  public int getSelectionStart() {
    return myDocument.hostToInjected(myHostModel.getSelectionStart());
  }

  @Nullable
  @Override
  public VisualPosition getSelectionStartPosition() {
    return myHostModel.getSelectionStartPosition();
  }

  @Override
  public int getSelectionEnd() {
    return myDocument.hostToInjected(myHostModel.getSelectionEnd());
  }

  @Nullable
  @Override
  public VisualPosition getSelectionEndPosition() {
    return myHostModel.getSelectionEndPosition();
  }

  @Override
  public String getSelectedText() {
    return myHostModel.getSelectedText();
  }

  @Nullable
  @Override
  public String getSelectedText(boolean allCarets) {
    return myHostModel.getSelectedText(allCarets);
  }

  @Override
  public int getLeadSelectionOffset() {
    return myDocument.hostToInjected(myHostModel.getLeadSelectionOffset());
  }

  @Nullable
  @Override
  public VisualPosition getLeadSelectionPosition() {
    return myHostModel.getLeadSelectionPosition();
  }

  @Override
  public boolean hasSelection() {
    return myHostModel.hasSelection();
  }

  @Override
  public boolean hasSelection(boolean anyCaret) {
    return myHostModel.hasSelection(anyCaret);
  }

  @Override
  public void setSelection(final int startOffset, final int endOffset) {
    TextRange hostRange = myDocument.injectedToHost(new ProperTextRange(startOffset, endOffset));
    myHostModel.setSelection(hostRange.getStartOffset(), hostRange.getEndOffset());
  }

  @Override
  public void setSelection(int startOffset, @Nullable VisualPosition endPosition, int endOffset) {
    TextRange hostRange = myDocument.injectedToHost(new ProperTextRange(startOffset, endOffset));
    myHostModel.setSelection(hostRange.getStartOffset(), endPosition, hostRange.getEndOffset());
  }

  @Override
  public void setSelection(@Nullable VisualPosition startPosition, int startOffset, @Nullable VisualPosition endPosition, int endOffset) {
    TextRange hostRange = myDocument.injectedToHost(new ProperTextRange(startOffset, endOffset));
    myHostModel.setSelection(startPosition, hostRange.getStartOffset(), endPosition, hostRange.getEndOffset());
  }

  @Override
  public void removeSelection() {
    myHostModel.removeSelection();
  }

  @Override
  public void removeSelection(boolean allCarets) {
    myHostModel.removeSelection(allCarets);
  }

  @Override
  public void addSelectionListener(final SelectionListener listener) {
    myHostModel.addSelectionListener(listener);
  }

  @Override
  public void removeSelectionListener(final SelectionListener listener) {
    myHostModel.removeSelectionListener(listener);
  }

  @Override
  public void selectLineAtCaret() {
    myHostModel.selectLineAtCaret();
  }

  @Override
  public void selectWordAtCaret(final boolean honorCamelWordsSettings) {
    myHostModel.selectWordAtCaret(honorCamelWordsSettings);
  }

  @Override
  public void copySelectionToClipboard() {
    myHostModel.copySelectionToClipboard();
  }

  @Override
  public void setBlockSelection(@NotNull final LogicalPosition blockStart, @NotNull final LogicalPosition blockEnd) {
    myHostModel.setBlockSelection(myInjectedEditor.injectedToHost(blockStart), myInjectedEditor.injectedToHost(blockEnd));
  }

  @Override
  @NotNull
  public int[] getBlockSelectionStarts() {
    int[] result = myHostModel.getBlockSelectionStarts();
    for (int i = 0; i < result.length; i++) {
      result[i] = myDocument.hostToInjected(result[i]);
    }
    return result;
  }

  @Override
  @NotNull
  public int[] getBlockSelectionEnds() {
    int[] result = myHostModel.getBlockSelectionEnds();
    for (int i = 0; i < result.length; i++) {
      result[i] = myDocument.hostToInjected(result[i]);
    }
    return result;
  }

  @Override
  public TextAttributes getTextAttributes() {
    return myHostModel.getTextAttributes();
  }
}
