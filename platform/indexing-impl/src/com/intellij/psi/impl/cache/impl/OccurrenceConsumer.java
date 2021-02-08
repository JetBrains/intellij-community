// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.cache.impl;

import com.intellij.psi.impl.cache.impl.id.IdDataConsumer;
import com.intellij.psi.search.IndexPattern;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public final class OccurrenceConsumer {
  private final IdDataConsumer myIndexDataConsumer;
  private Object2IntMap<IndexPattern> myTodoOccurrences;
  private final boolean myNeedToDo;

  public OccurrenceConsumer(IdDataConsumer indexDataConsumer, boolean needToDo) {
    myIndexDataConsumer = indexDataConsumer;
    myNeedToDo = needToDo;
  }

  public void addOccurrence(final CharSequence charSequence, char[] charArray, final int start, final int end, final int occurrenceMask) {
    if (myIndexDataConsumer == null) {
      return;
    }

    if (charArray != null) {
      myIndexDataConsumer.addOccurrence(charArray, start, end, occurrenceMask);
    }
    else {
      myIndexDataConsumer.addOccurrence(charSequence, start, end, occurrenceMask);
    }
  }

  public void incTodoOccurrence(final IndexPattern pattern) {
    if (myTodoOccurrences == null) {
      myTodoOccurrences = new Object2IntOpenHashMap<>();
    }
    myTodoOccurrences.mergeInt(pattern, 1, Math::addExact);
  }

  public int getOccurrenceCount(IndexPattern pattern) {
    return myTodoOccurrences == null ? 0 : myTodoOccurrences.getInt(pattern);
  }

  public boolean isNeedToDo() {
    return myNeedToDo;
  }
}
