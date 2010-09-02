/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BaseFilterLexer extends DelegateLexer implements IdTableBuilding.ScanWordProcessor {
  private final OccurrenceConsumer myOccurrenceConsumer;

  private int myTodoScannedBound = 0;
  private int myOccurenceMask;
  private TodoScanningData[] myTodoScanningData;
  private CharSequence myCachedBufferSequence;

  public interface OccurrenceConsumer {
    void addOccurrence(CharSequence charSequence, int start, int end, int occurrenceMask);

    boolean canConsumeTodoOccurrences();
    void incTodoOccurrence(IndexPattern pattern);
  }
  
  protected BaseFilterLexer(Lexer originalLexer, OccurrenceConsumer occurrenceConsumer) {
    super(originalLexer);
    myOccurrenceConsumer = occurrenceConsumer;
  }

  protected final void advanceTodoItemCountsInToken() {
    if (myOccurrenceConsumer.canConsumeTodoOccurrences()){
      int start = getTokenStart();
      int end = getTokenEnd();
      start = Math.max(start, myTodoScannedBound);
      if (start >= end) return; // this prevents scanning of the same comment twice

      CharSequence input = getBufferSequence().subSequence(start, end);
      myTodoScanningData = advanceTodoItemsCount(input, myOccurrenceConsumer, myTodoScanningData);

      myTodoScannedBound = end;
    }
  }

  public static class TodoScanningData {
    final IndexPattern pattern;
    final Matcher matcher;

    public TodoScanningData(IndexPattern pattern, Matcher matcher) {
      this.matcher = matcher;
      this.pattern = pattern;
    }
  }

  public static TodoScanningData[] advanceTodoItemsCount(final CharSequence input, final OccurrenceConsumer consumer, TodoScanningData[] todoScanningData) {
    if (todoScanningData == null) {
      IndexPattern[] patterns = CacheUtil.getIndexPatterns();
      todoScanningData = new TodoScanningData[patterns.length];

      for (int i = 0; i < patterns.length; ++i) {
        IndexPattern indexPattern = patterns[i];
        Pattern pattern = indexPattern.getPattern();

        if (pattern != null) {
          todoScanningData [i] = new TodoScanningData(indexPattern, pattern.matcher(""));
        }
      }
    }

    for (TodoScanningData data:todoScanningData) {
      if (data == null) continue;
      Matcher matcher = data.matcher;
      matcher.reset(input);

      while (matcher.find()) {
        if (matcher.start() != matcher.end()) {
          consumer.incTodoOccurrence(data.pattern);
        }
      }
    }

    return todoScanningData;
  }

  public final void run(CharSequence chars, int start, int end) {
    myOccurrenceConsumer.addOccurrence(chars, start, end, myOccurenceMask);
  }

  protected final void addOccurrenceInToken(final int occurrenceMask) {
    myOccurrenceConsumer.addOccurrence(getBufferSequence(), getTokenStart(), getTokenEnd(), occurrenceMask);
  }

  protected final void scanWordsInToken(final int occurrenceMask, boolean mayHaveFileRefs, final boolean mayHaveEscapes) {
    myOccurenceMask = occurrenceMask;
    final int start = getTokenStart();
    final int end = getTokenEnd();
    if (myCachedBufferSequence == null) myCachedBufferSequence = getBufferSequence();
    IdTableBuilding.scanWords(this, myCachedBufferSequence, null, start, end, mayHaveEscapes);

    if (mayHaveFileRefs) {
      processPossibleComplexFileName(myCachedBufferSequence, start, end);
    }
  }
  
  private void processPossibleComplexFileName(CharSequence chars, int startOffset, int endOffset) {
    int offset = findCharsWithinRange(chars, startOffset, endOffset, "/\\");
    offset = Math.min(offset, endOffset);
    int start = startOffset;

    while(start < endOffset) {
      if (start != offset) {
        myOccurrenceConsumer.addOccurrence(chars, start, offset, UsageSearchContext.IN_FOREIGN_LANGUAGES);
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
  
}
