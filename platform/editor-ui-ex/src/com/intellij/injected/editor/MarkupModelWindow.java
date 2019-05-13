/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.MarkupIterator;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
*/
public class MarkupModelWindow extends UserDataHolderBase implements MarkupModelEx {
  private final DocumentWindow myDocument;
  private final MarkupModelEx myHostModel;

  public MarkupModelWindow(MarkupModelEx editorMarkupModel, final DocumentWindow document) {
    myDocument = document;
    myHostModel = editorMarkupModel;
  }

  @Override
  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  @Override
  @NotNull
  public RangeHighlighter addRangeHighlighter(final int startOffset,
                                              final int endOffset,
                                              final int layer,
                                              final TextAttributes textAttributes,
                                              @NotNull final HighlighterTargetArea targetArea) {
    TextRange hostRange = myDocument.injectedToHost(new ProperTextRange(startOffset, endOffset));
    return myHostModel.addRangeHighlighter(hostRange.getStartOffset(), hostRange.getEndOffset(), layer, textAttributes, targetArea);
  }

  @NotNull
  @Override
  public RangeHighlighterEx addRangeHighlighterAndChangeAttributes(int startOffset,
                                                                   int endOffset,
                                                                   int layer,
                                                                   TextAttributes textAttributes,
                                                                   @NotNull HighlighterTargetArea targetArea,
                                                                   boolean isPersistent,
                                                                   Consumer<RangeHighlighterEx> changeAttributesAction) {
    TextRange hostRange = myDocument.injectedToHost(new ProperTextRange(startOffset, endOffset));
    return myHostModel.addRangeHighlighterAndChangeAttributes(hostRange.getStartOffset(), hostRange.getEndOffset(), layer, textAttributes,
                                                              targetArea, isPersistent, changeAttributesAction);
  }

  @Override
  public void changeAttributesInBatch(@NotNull RangeHighlighterEx highlighter,
                                      @NotNull Consumer<RangeHighlighterEx> changeAttributesAction) {
    myHostModel.changeAttributesInBatch(highlighter, changeAttributesAction);
  }

  @Override
  @NotNull
  public RangeHighlighter addLineHighlighter(final int line, final int layer, final TextAttributes textAttributes) {
    int hostLine = myDocument.injectedToHostLine(line);
    return myHostModel.addLineHighlighter(hostLine, layer, textAttributes);
  }

  @Override
  public void removeHighlighter(@NotNull final RangeHighlighter rangeHighlighter) {
    myHostModel.removeHighlighter(rangeHighlighter);
  }

  @Override
  public void removeAllHighlighters() {
    myHostModel.removeAllHighlighters();
  }

  @Override
  @NotNull
  public RangeHighlighter[] getAllHighlighters() {
    return myHostModel.getAllHighlighters();
  }

  @Override
  public void dispose() {
    myHostModel.dispose();
  }

  @Override
  public RangeHighlighterEx addPersistentLineHighlighter(final int line, final int layer, final TextAttributes textAttributes) {
    int hostLine = myDocument.injectedToHostLine(line);
    return myHostModel.addPersistentLineHighlighter(hostLine, layer, textAttributes);
  }


  @Override
  public boolean containsHighlighter(@NotNull final RangeHighlighter highlighter) {
    return myHostModel.containsHighlighter(highlighter);
  }

  @Override
  public void addMarkupModelListener(@NotNull Disposable parentDisposable, @NotNull MarkupModelListener listener) {
    myHostModel.addMarkupModelListener(parentDisposable, listener);
  }

  @Override
  public void setRangeHighlighterAttributes(@NotNull final RangeHighlighter highlighter, @NotNull final TextAttributes textAttributes) {
    myHostModel.setRangeHighlighterAttributes(highlighter, textAttributes);
  }

  @Override
  public boolean processRangeHighlightersOverlappingWith(int start, int end, @NotNull Processor<? super RangeHighlighterEx> processor) {
    //todo
    return false;
  }

  @Override
  public boolean processRangeHighlightersOutside(int start, int end, @NotNull Processor<? super RangeHighlighterEx> processor) {
    //todo
    return false;
  }

  @NotNull
  @Override
  public MarkupIterator<RangeHighlighterEx> overlappingIterator(int startOffset, int endOffset) {
    // todo convert
    return myHostModel.overlappingIterator(startOffset, endOffset);
  }

  @Override
  public void fireAttributesChanged(@NotNull RangeHighlighterEx segmentHighlighter, boolean renderersChanged, boolean fontStyleChanged) {

  }

  @Override
  public void fireAfterAdded(@NotNull RangeHighlighterEx segmentHighlighter) {

  }

  @Override
  public void fireBeforeRemoved(@NotNull RangeHighlighterEx segmentHighlighter) {

  }

  @Override
  public void addRangeHighlighter(@NotNull RangeHighlighterEx marker, int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer) {

  }
}
