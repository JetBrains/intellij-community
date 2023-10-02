// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.injected.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
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
import org.jetbrains.annotations.Nullable;

public class MarkupModelWindow extends UserDataHolderBase implements MarkupModelEx {
  private final DocumentWindow myDocument;
  private final MarkupModelEx myHostModel;

  public MarkupModelWindow(@NotNull MarkupModelEx editorMarkupModel, @NotNull DocumentWindow document) {
    myDocument = document;
    myHostModel = editorMarkupModel;
  }

  @Override
  public @NotNull Document getDocument() {
    return myDocument;
  }

  @Override
  public @NotNull RangeHighlighter addRangeHighlighter(final @Nullable TextAttributesKey textAttributesKey,
                                                       final int startOffset,
                                                       final int endOffset,
                                                       final int layer,
                                                       final @NotNull HighlighterTargetArea targetArea) {
    TextRange hostRange = myDocument.injectedToHost(new ProperTextRange(startOffset, endOffset));
    return myHostModel.addRangeHighlighter(
      textAttributesKey, hostRange.getStartOffset(), hostRange.getEndOffset(), layer, targetArea);
  }

  @Override
  public @NotNull RangeHighlighter addRangeHighlighter(int startOffset,
                                                       int endOffset,
                                                       int layer,
                                                       @Nullable TextAttributes textAttributes,
                                                       @NotNull HighlighterTargetArea targetArea) {
    TextRange hostRange = myDocument.injectedToHost(new ProperTextRange(startOffset, endOffset));
    return myHostModel.addRangeHighlighter(
      hostRange.getStartOffset(), hostRange.getEndOffset(), layer, textAttributes, targetArea);
  }

  @Override
  public @NotNull RangeHighlighterEx addRangeHighlighterAndChangeAttributes(@Nullable TextAttributesKey textAttributesKey,
                                                                            int startOffset,
                                                                            int endOffset,
                                                                            int layer,
                                                                            @NotNull HighlighterTargetArea targetArea,
                                                                            boolean isPersistent,
                                                                            @Nullable Consumer<? super RangeHighlighterEx> changeAttributesAction) {
    TextRange hostRange = myDocument.injectedToHost(new ProperTextRange(startOffset, endOffset));
    return myHostModel.addRangeHighlighterAndChangeAttributes(textAttributesKey, hostRange.getStartOffset(), hostRange.getEndOffset(), layer,
                                                              targetArea, isPersistent, changeAttributesAction);
  }

  @Override
  public void changeAttributesInBatch(@NotNull RangeHighlighterEx highlighter,
                                      @NotNull Consumer<? super RangeHighlighterEx> changeAttributesAction) {
    myHostModel.changeAttributesInBatch(highlighter, changeAttributesAction);
  }

  @Override
  public @NotNull RangeHighlighter addLineHighlighter(final @Nullable TextAttributesKey textAttributesKey,
                                                      final int line,
                                                      final int layer) {
    int hostLine = myDocument.injectedToHostLine(line);
    return myHostModel.addLineHighlighter(textAttributesKey, hostLine, layer);
  }

  @Override
  public @NotNull RangeHighlighter addLineHighlighter(int line, int layer, @Nullable TextAttributes textAttributes) {
    int hostLine = myDocument.injectedToHostLine(line);
    return myHostModel.addLineHighlighter(hostLine, layer, textAttributes);
  }

  @Override
  public void removeHighlighter(final @NotNull RangeHighlighter rangeHighlighter) {
    myHostModel.removeHighlighter(rangeHighlighter);
  }

  @Override
  public void removeAllHighlighters() {
    myHostModel.removeAllHighlighters();
  }

  @Override
  public RangeHighlighter @NotNull [] getAllHighlighters() {
    return myHostModel.getAllHighlighters();
  }

  @Override
  public void dispose() {
    myHostModel.dispose();
  }

  @Override
  public RangeHighlighterEx addPersistentLineHighlighter(final @Nullable TextAttributesKey textAttributesKey,
                                                         final int line,
                                                         final int layer) {
    int hostLine = myDocument.injectedToHostLine(line);
    return myHostModel.addPersistentLineHighlighter(textAttributesKey, hostLine, layer);
  }

  @Override
  public @Nullable RangeHighlighterEx addPersistentLineHighlighter(int lineNumber, int layer, @Nullable TextAttributes textAttributes) {
    int hostLine = myDocument.injectedToHostLine(lineNumber);
    return myHostModel.addPersistentLineHighlighter(hostLine, layer, textAttributes);
  }


  @Override
  public boolean containsHighlighter(final @NotNull RangeHighlighter highlighter) {
    return myHostModel.containsHighlighter(highlighter);
  }

  @Override
  public void addMarkupModelListener(@NotNull Disposable parentDisposable, @NotNull MarkupModelListener listener) {
    myHostModel.addMarkupModelListener(parentDisposable, listener);
  }

  @Override
  public void setRangeHighlighterAttributes(final @NotNull RangeHighlighter highlighter, final @NotNull TextAttributes textAttributes) {
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

  @Override
  public @NotNull MarkupIterator<RangeHighlighterEx> overlappingIterator(int startOffset, int endOffset) {
    // todo convert
    return myHostModel.overlappingIterator(startOffset, endOffset);
  }
}
