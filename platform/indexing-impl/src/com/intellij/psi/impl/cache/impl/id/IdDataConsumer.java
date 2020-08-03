// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.cache.impl.id;

import com.intellij.util.text.CharArrayCharSequence;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class IdDataConsumer {
  @NotNull
  private final Map<IdIndexEntry, Integer> myResult = new THashMap<>();

  @NotNull
  public Map<IdIndexEntry, Integer> getResult() {
    return myResult;
  }
  
  public void addOccurrence(CharSequence charSequence, int start, int end, int occurrenceMask) {
    if (end == start) return;
    final IdIndexEntry entry = new IdIndexEntry(charSequence, start, end, true);
    addOccurrence(entry, occurrenceMask);

    int hashNoCase = IdIndexEntry.getWordHash(charSequence, start, end, false);
    if (hashNoCase != entry.getWordHashCode()) {
      addOccurrence(new IdIndexEntry(hashNoCase), occurrenceMask);
    }
  }

  public void addOccurrence(char[] chars, int start, int end, int occurrenceMask) {
    addOccurrence(new CharArrayCharSequence(chars), start, end, occurrenceMask);
  }

  private void addOccurrence(@NotNull IdIndexEntry entry, int occurrenceMask) {
    if (occurrenceMask != 0) {
      final int old = myResult.getOrDefault(entry, 0);
      int v = old | occurrenceMask;
      if (v != old) {
        myResult.put(entry, v);
      }
    }
  }
}
