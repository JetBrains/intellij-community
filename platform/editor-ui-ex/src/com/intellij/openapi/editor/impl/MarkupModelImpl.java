/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 19, 2002
 * Time: 2:26:19 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MarkupModelImpl extends UserDataHolderBase implements MarkupModelEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.MarkupModelImpl");
  private final DocumentEx myDocument;

  private RangeHighlighter[] myCachedHighlighters;
  private final List<MarkupModelListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final RangeHighlighterTree myHighlighterTree;

  MarkupModelImpl(@NotNull DocumentEx document) {
    myDocument = document;
    myHighlighterTree = new RangeHighlighterTree(myDocument, this);
  }

  @Override
  public void dispose() {
    myHighlighterTree.dispose();
  }

  @Override
  @NotNull
  public RangeHighlighter addLineHighlighter(int lineNumber, int layer, TextAttributes textAttributes) {
    if (lineNumber >= getDocument().getLineCount() || lineNumber < 0) {
      throw new IndexOutOfBoundsException("lineNumber:" + lineNumber + ". Must be in [0, " + (getDocument().getLineCount() - 1) + "]");
    }

    // The rationale why we don't bind to the line start offset here is that following: suppose particular breakpoint is hit
    // during debugging. We may want to type <enter> at the active line indent and highlighted string will be moved one line
    // down as well then.
    int offset = getFirstNonspaceCharOffset(getDocument(), lineNumber);

    return addRangeHighlighter(offset, offset, layer, textAttributes, HighlighterTargetArea.LINES_IN_RANGE);
  }

  @Override
  public RangeHighlighter addPersistentLineHighlighter(int lineNumber, int layer, TextAttributes textAttributes) {
    if (lineNumber >= getDocument().getLineCount() || lineNumber < 0) return null;

    int offset = getFirstNonspaceCharOffset(getDocument(), lineNumber);

    return addRangeHighlighterAndChangeAttributes(offset, offset, layer, textAttributes, HighlighterTargetArea.LINES_IN_RANGE, true, null);
  }

  private static int getFirstNonspaceCharOffset(@NotNull Document doc, int lineNumber) {
    int lineStart = doc.getLineStartOffset(lineNumber);
    int lineEnd = doc.getLineEndOffset(lineNumber);
    CharSequence text = doc.getCharsSequence();
    int offset = lineStart;
    for (int i = lineStart; i < lineEnd; i++) {
      char c = text.charAt(i);
      if (c != ' ' && c != '\t') {
        offset = i;
        break;
      }
    }
    return offset;
  }

  // NB: Can return invalid highlighters
  @Override
  @NotNull
  public RangeHighlighter[] getAllHighlighters() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myCachedHighlighters == null) {
      int size = myHighlighterTree.size();
      if (size == 0) return RangeHighlighter.EMPTY_ARRAY;
      List<RangeHighlighterEx> list = new ArrayList<RangeHighlighterEx>(size);
      myHighlighterTree.process(new CommonProcessors.CollectProcessor<RangeHighlighterEx>(list));
      myCachedHighlighters = list.toArray(new RangeHighlighter[list.size()]);
    }
    return myCachedHighlighters;
  }

  @Override
  public RangeHighlighterEx addRangeHighlighterAndChangeAttributes(int startOffset,
                                                                   int endOffset,
                                                                   int layer,
                                                                   TextAttributes textAttributes,
                                                                   @NotNull HighlighterTargetArea targetArea,
                                                                   boolean isPersistent,
                                                                   @Nullable Consumer<RangeHighlighterEx> changeAttributesAction) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    RangeHighlighterEx highlighter = isPersistent
                                     ? new PersistentRangeHighlighterImpl(this, startOffset, layer, targetArea, textAttributes)
                                     : new RangeHighlighterImpl(this, startOffset, endOffset, layer, targetArea, textAttributes, false,
                                                                false);

    myCachedHighlighters = null;
    if (changeAttributesAction != null) {
      ((RangeHighlighterImpl)highlighter).changeAttributesNoEvents(changeAttributesAction);
    }
    fireAfterAdded(highlighter);
    return highlighter;
  }

  @Override
  public void changeAttributesInBatch(@NotNull RangeHighlighterEx highlighter,
                                      @NotNull Consumer<RangeHighlighterEx> changeAttributesAction) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    boolean changed = ((RangeHighlighterImpl)highlighter).changeAttributesNoEvents(changeAttributesAction);
    if (changed) {
      fireAttributesChanged(highlighter);
    }
  }

  @Override
  public void addRangeHighlighter(RangeHighlighterEx marker,
                                  int start,
                                  int end,
                                  boolean greedyToLeft,
                                  boolean greedyToRight,
                                  int layer) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myHighlighterTree.addInterval(marker, start, end, greedyToLeft, greedyToRight, layer);
  }

  @Override
  @NotNull
  public RangeHighlighter addRangeHighlighter(int startOffset,
                                              int endOffset,
                                              int layer,
                                              TextAttributes textAttributes,
                                              @NotNull HighlighterTargetArea targetArea) {
    return addRangeHighlighterAndChangeAttributes(startOffset, endOffset, layer, textAttributes, targetArea, false, null);
  }

  @Override
  public void removeHighlighter(@NotNull RangeHighlighter segmentHighlighter) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myCachedHighlighters = null;
    if (!segmentHighlighter.isValid()) return;

    boolean removed = myHighlighterTree.removeInterval((RangeHighlighterEx)segmentHighlighter);
    LOG.assertTrue(removed);
  }

  @Override
  public void removeAllHighlighters() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myCachedHighlighters = null;
    myHighlighterTree.clear();
  }

  @Override
  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  @Override
  public void addMarkupModelListener(@NotNull Disposable parentDisposable, @NotNull final MarkupModelListener listener) {
    myListeners.add(listener);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        removeMarkupModelListener(listener);
      }
    });
  }

  public void removeMarkupModelListener(@NotNull MarkupModelListener listener) {
    boolean success = myListeners.remove(listener);
    LOG.assertTrue(success);
  }

  @Override
  public void setRangeHighlighterAttributes(@NotNull final RangeHighlighter highlighter, @NotNull final TextAttributes textAttributes) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ((RangeHighlighterEx)highlighter).setTextAttributes(textAttributes);
  }

  @Override
  public void fireAttributesChanged(@NotNull RangeHighlighterEx segmentHighlighter) {
    for (MarkupModelListener listener : myListeners) {
      listener.attributesChanged(segmentHighlighter);
    }
  }

  @Override
  public void fireAfterAdded(@NotNull RangeHighlighterEx segmentHighlighter) {
    for (MarkupModelListener listener : myListeners) {
      listener.afterAdded(segmentHighlighter);
    }
  }

  @Override
  public void fireBeforeRemoved(@NotNull RangeHighlighterEx segmentHighlighter) {
    for (MarkupModelListener listener : myListeners) {
      listener.beforeRemoved(segmentHighlighter);
    }
  }

  @Override
  public boolean containsHighlighter(@NotNull final RangeHighlighter highlighter) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return !myHighlighterTree
      .processOverlappingWith(highlighter.getStartOffset(), highlighter.getEndOffset(), new Processor<RangeHighlighterEx>() {
        @Override
        public boolean process(RangeHighlighterEx h) {
          return h.getId() != ((RangeHighlighterEx)highlighter).getId();
        }
      });
  }

  @Override
  public boolean processRangeHighlightersOverlappingWith(int start, int end, @NotNull Processor<? super RangeHighlighterEx> processor) {
    return myHighlighterTree.processOverlappingWith(start, end, processor);
  }

  @Override
  public boolean processRangeHighlightersOutside(int start, int end, @NotNull Processor<? super RangeHighlighterEx> processor) {
    return myHighlighterTree.processOverlappingWithOutside(start, end, processor);
  }

  @Override
  @NotNull
  public DisposableIterator<RangeHighlighterEx> overlappingIterator(int startOffset, int endOffset) {
    return myHighlighterTree.overlappingIterator(startOffset, endOffset);
  }

  @Override
  public boolean sweep(int start, int end, @NotNull SweepProcessor<RangeHighlighterEx> sweepProcessor) {
    return myHighlighterTree.sweep(start, end, sweepProcessor);
  }
}
