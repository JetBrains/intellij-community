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

package com.intellij.psi.impl.cache.impl;

import com.intellij.lexer.DelegateLexer;
import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.cache.impl.id.IdTableBuilding;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BaseFilterLexer extends DelegateLexer implements IdTableBuilding.ScanWordProcessor {
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
    myTodoScanningState = advanceTodoItemsCount(input, myOccurrenceConsumer, myTodoScanningState);

    myTodoScannedBound = end;
  }

  public static class TodoScanningState {
    final IndexPattern[] myPatterns;
    final Matcher[] myMatchers;
    TIntArrayList myOccurences;

    public TodoScanningState(IndexPattern[] patterns, Matcher[] matchers) {
      myPatterns = patterns;
      myMatchers = matchers;
      myOccurences = new TIntArrayList(1);
    }
  }

  public static TodoScanningState advanceTodoItemsCount(final CharSequence input, final OccurrenceConsumer consumer, TodoScanningState todoScanningState) {
    if (todoScanningState == null) {
      IndexPattern[] patterns = IndexPatternUtil.getIndexPatterns();

      Matcher[] matchers = new Matcher[patterns.length];
      todoScanningState = new TodoScanningState(patterns, matchers);

      for (int i = 0; i < patterns.length; ++i) {
        Pattern pattern = patterns[i].getOptimizedIndexingPattern();

        if (pattern != null) {
          matchers[i] = pattern.matcher("");
        }
      }
    } else {
      todoScanningState.myOccurences.resetQuick();
    }

    for (int i = todoScanningState.myMatchers.length - 1; i >= 0; --i) {
      Matcher matcher = todoScanningState.myMatchers[i];
      if (matcher == null) continue;
      matcher.reset(input);

      while (matcher.find()) {
        int start = matcher.start();
        if (start != matcher.end() && todoScanningState.myOccurences.indexOf(start) == -1) {
          consumer.incTodoOccurrence(todoScanningState.myPatterns[i]);
          todoScanningState.myOccurences.add(start);
        }
      }
    }

    return todoScanningState;
  }

  @Override
  public final void run(CharSequence chars, @Nullable char[] charsArray, int start, int end) {
    myOccurrenceConsumer.addOccurrence(chars, charsArray, start, end, myOccurenceMask);
  }

  protected final void addOccurrenceInToken(final int occurrenceMask) {
    myOccurrenceConsumer.addOccurrence(myCachedBufferSequence, myCachedArraySequence, getTokenStart(), getTokenEnd(), occurrenceMask);
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
