// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.cache.impl.id;

import com.intellij.util.text.CharArrayCharSequence;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class IdDataConsumer {
  @NotNull
  private final Object2IntOpenHashMap<IdIndexEntry> myResult = new Object2IntOpenHashMap<>();

  @NotNull
  public Map<IdIndexEntry, Integer> getResult() {
    return myResult;
  }

  public void addOccurrence(char[] chars, int start, int end, int occurrenceMask) {
    addOccurrence(new CharArrayCharSequence(chars), start, end, occurrenceMask);
  }

  public void addOccurrence(CharSequence charSequence, int start, int end, int occurrenceMask) {
    if (end == start || occurrenceMask == 0) return;
    int hash = IdIndexEntry.getWordHash(charSequence, start, end, true);
    myResult.mergeInt(new IdIndexEntry(hash), occurrenceMask, (prev, cur) -> prev | cur);

    int hashNoCase = IdIndexEntry.getWordHash(charSequence, start, end, false);
    if (hashNoCase != hash) {
      myResult.mergeInt(new IdIndexEntry(hashNoCase), occurrenceMask, (prev, cur) -> prev | cur);
    }
  }
}
