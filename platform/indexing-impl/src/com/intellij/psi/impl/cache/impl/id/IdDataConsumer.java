// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.cache.impl.id;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.CharArrayCharSequence;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class IdDataConsumer {
  private final @NotNull Object2IntMap<IdIndexEntry> myResult = new Object2IntOpenHashMap<>();

  public @NotNull Map<IdIndexEntry, Integer> getResult() {
    return myResult;
  }

  public void addOccurrence(char[] chars, int start, int end, int occurrenceMask) {
    addOccurrence(new CharArrayCharSequence(chars), start, end, occurrenceMask);
  }

  public void addOccurrence(CharSequence charSequence, int start, int end, int occurrenceMask) {
    if (end == start || occurrenceMask == 0) return;
    int hash, hashNoCase;
    if (IdIndexEntry.useStrongerHash()) {
      hash = hashNoCase = 0;
      boolean different = false;
      for (int off = start; off < end; off++) {
        char c = charSequence.charAt(off);
        char lowerC = StringUtil.toLowerCase(c);
        if (!different && c != lowerC) {
          different = true;
          hashNoCase = hash;
        }
        hash = 31 * hash + c;
        if (different) {
          hashNoCase = 31 * hashNoCase + lowerC;
        }
      }
      if (!different) {
        hashNoCase = hash;
      }
    }
    else {
      char firstChar = charSequence.charAt(start);
      char lastChar = charSequence.charAt(end - 1);
      hash = (firstChar << 8) + (lastChar << 4) + end - start;
      char firstCharLower = StringUtil.toLowerCase(firstChar);
      char lastCharLower = StringUtil.toLowerCase(lastChar);
      hashNoCase = (firstCharLower == firstChar && lastCharLower == lastChar) ? hash : 
                   (firstCharLower << 8) + (lastCharLower << 4) + end - start;
    }
    myResult.mergeInt(new IdIndexEntry(hash), occurrenceMask, (prev, cur) -> prev | cur);

    if (hashNoCase != hash) {
      myResult.mergeInt(new IdIndexEntry(hashNoCase), occurrenceMask, (prev, cur) -> prev | cur);
    }
  }
}
