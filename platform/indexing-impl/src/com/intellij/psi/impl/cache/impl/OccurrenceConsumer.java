/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.psi.search.IndexPattern;
import com.intellij.util.indexing.IdDataConsumer;
import gnu.trove.TObjectIntHashMap;

public final class OccurrenceConsumer {
  private final IdDataConsumer myIndexDataConsumer;
  private TObjectIntHashMap<IndexPattern> myTodoOccurrences;
  private final boolean myNeedToDo;

  public OccurrenceConsumer(final IdDataConsumer indexDataConsumer, boolean needToDo) {
    myIndexDataConsumer = indexDataConsumer;
    myNeedToDo = needToDo;
  }

  public void addOccurrence(final CharSequence charSequence, char[] charArray, final int start, final int end, final int occurrenceMask) {
    if (myIndexDataConsumer == null) return;
    if (charArray != null) {
      myIndexDataConsumer.addOccurrence(charArray, start, end, occurrenceMask);
    } else {
      myIndexDataConsumer.addOccurrence(charSequence, start, end, occurrenceMask);
    }
  }

  public void incTodoOccurrence(final IndexPattern pattern) {
    if (myTodoOccurrences == null) {
      myTodoOccurrences = new TObjectIntHashMap<>();
      for (IndexPattern indexPattern : IndexPatternUtil.getIndexPatterns()) {
        myTodoOccurrences.put(indexPattern, 0);
      }
    }
    myTodoOccurrences.adjustValue(pattern, 1);
  }

  public int getOccurrenceCount(IndexPattern pattern) {
    if (myTodoOccurrences == null) return 0;
    return myTodoOccurrences.get(pattern);
  }

  public boolean isNeedToDo() {
    return myNeedToDo;
  }
}
