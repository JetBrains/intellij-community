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

package com.intellij.psi.impl.cache.impl.todo;

import com.intellij.psi.impl.cache.impl.BaseFilterLexer;
import com.intellij.psi.impl.cache.impl.IndexPatternUtil;
import com.intellij.psi.search.IndexPattern;
import gnu.trove.TObjectIntHashMap;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 17, 2008
 */
public class TodoOccurrenceConsumer implements BaseFilterLexer.OccurrenceConsumer {
  private final TObjectIntHashMap<IndexPattern> myTodoOccurrences;
  
  public TodoOccurrenceConsumer() {
    myTodoOccurrences = new TObjectIntHashMap<IndexPattern>();
    for (IndexPattern pattern : IndexPatternUtil.getIndexPatterns()) {
      myTodoOccurrences.put(pattern, 0);
    }
  }
  
  @Override
  public void addOccurrence(final CharSequence charSequence, char[] charArray, final int start, final int end, final int occurrenceMask) {
    // empty
  }

  @Override
  public void incTodoOccurrence(final IndexPattern pattern) {
    myTodoOccurrences.adjustValue(pattern, 1);
  }

  @Override
  public boolean canConsumeTodoOccurrences() {
    return true;
  }
  
  public int getOccurrenceCount(IndexPattern pattern) {
    return myTodoOccurrences.get(pattern); 
  }
}
