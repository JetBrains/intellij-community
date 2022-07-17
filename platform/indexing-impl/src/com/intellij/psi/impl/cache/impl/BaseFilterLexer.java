// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.cache.impl;

import com.intellij.lexer.DelegateLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.impl.cache.impl.id.IdTableBuilding;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.util.text.CharArrayUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BaseFilterLexer extends DelegateLexer implements IdTableBuilding.ScanWordProcessor {
  private static final Logger LOG = Logger.getInstance(BaseFilterLexer.class);

  private final OccurrenceConsumer myOccurrenceConsumer;

  private int myTodoScannedBound = 0;
  private int myOccurenceMask;
  private TodoScanningState myTodoScanningState;
  private CharSequence myCachedBufferSequence;
  private char[] myCachedArraySequence;

  protected BaseFilterLexer(Lexer originalLexer, OccurrenceConsumer occurrenceConsumer) {
    super(originalLexer);
    myOccurrenceConsumer = occurrenceConsumer;
  }

  protected final void advanceTodoItemCountsInToken() {
    if (!myOccurrenceConsumer.isNeedToDo()) return;
    int start = getTokenStart();
    int end = getTokenEnd();
    start = Math.max(start, myTodoScannedBound);
    if (start >= end) return; // this prevents scanning of the same comment twice

    CharSequence input = myCachedBufferSequence.subSequence(start, end);
    if (myTodoScanningState == null) myTodoScanningState = createTodoScanningState(IndexPatternUtil.getIndexPatterns());
    advanceTodoItemsCount(input, myOccurrenceConsumer, myTodoScanningState);

    myTodoScannedBound = end;
  }

  public static final class TodoScanningState {
    final IndexPattern[] myPatterns;
    final Matcher[] myMatchers;
    final IntList myOccurrences;

    public TodoScanningState(IndexPattern[] patterns, Matcher[] matchers) {
      myPatterns = patterns;
      myMatchers = matchers;
      myOccurrences = new IntArrayList(1);
    }
  }

  @NotNull
  public static TodoScanningState createTodoScanningState(IndexPattern[] patterns) {
    Matcher[] matchers = new Matcher[patterns.length];
    TodoScanningState todoScanningState = new TodoScanningState(patterns, matchers);

    for (int i = 0; i < patterns.length; ++i) {
      Pattern pattern = patterns[i].getOptimizedIndexingPattern();

      if (pattern != null) {
        matchers[i] = pattern.matcher("");
      }
    }
    return todoScanningState;
  }

  public static void advanceTodoItemsCount(CharSequence input, OccurrenceConsumer consumer, TodoScanningState todoScanningState) {
    todoScanningState.myOccurrences.clear();

    for (int i = todoScanningState.myMatchers.length - 1; i >= 0; --i) {
      Matcher matcher = todoScanningState.myMatchers[i];
      if (matcher == null) continue;
      matcher.reset(input);

      try {
        while (matcher.find()) {
          ProgressManager.checkCanceled();
          int start = matcher.start();
          if (start != matcher.end() && !todoScanningState.myOccurrences.contains(start)) {
            consumer.incTodoOccurrence(todoScanningState.myPatterns[i]);
            todoScanningState.myOccurrences.add(start);
          }
        }
      }
      catch (StackOverflowError error) {
        LOG.error(error); // do not reindex file, just ignore it
      }
    }
  }

  @Override
  public final void run(CharSequence chars, char @Nullable [] charsArray, int start, int end) {
    myOccurrenceConsumer.addOccurrence(chars, charsArray, start, end, myOccurenceMask);
  }

  protected final void addOccurrenceInToken(final int occurrenceMask) {
    myOccurrenceConsumer.addOccurrence(myCachedBufferSequence, myCachedArraySequence, getTokenStart(), getTokenEnd(), occurrenceMask);
  }

  protected final void addOccurrenceInToken(final int occurrenceMask, final int offset, final int length) {
    myOccurrenceConsumer.addOccurrence(myCachedBufferSequence, myCachedArraySequence, getTokenStart() + offset,
                                       Math.min(getTokenStart() + offset + length, getTokenEnd()), occurrenceMask);
  }

  protected final void scanWordsInToken(final int occurrenceMask, boolean mayHaveFileRefs, final boolean mayHaveEscapes) {
    myOccurenceMask = occurrenceMask;
    final int start = getTokenStart();
    final int end = getTokenEnd();
    IdTableBuilding.scanWords(this, myCachedBufferSequence, myCachedArraySequence, start, end, mayHaveEscapes);

    if (mayHaveFileRefs) {
      processPossibleComplexFileName(myCachedBufferSequence, myCachedArraySequence, start, end);
    }
  }

  private void processPossibleComplexFileName(CharSequence chars, char[] cachedArraySequence, int startOffset, int endOffset) {
    int offset = findCharsWithinRange(chars, startOffset, endOffset, "/\\");
    offset = Math.min(offset, endOffset);
    int start = startOffset;

    while(start < endOffset) {
      if (start != offset) {
        myOccurrenceConsumer.addOccurrence(chars, cachedArraySequence, start, offset, UsageSearchContext.IN_FOREIGN_LANGUAGES);
      }
      start = offset + 1;
      offset = Math.min(endOffset, findCharsWithinRange(chars, start, endOffset, "/\\"));
    }
  }

  private static int findCharsWithinRange(final CharSequence chars, int startOffset, int endOffset, String charsToFind) {
    while(startOffset < endOffset) {
      if (charsToFind.indexOf(chars.charAt(startOffset)) != -1) {
        return startOffset;
      }
      ++startOffset;
    }

    return startOffset;
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    super.start(buffer, startOffset, endOffset, initialState);
    myCachedBufferSequence = getBufferSequence();
    myCachedArraySequence = CharArrayUtil.fromSequenceWithoutCopying(myCachedBufferSequence);
  }
}
