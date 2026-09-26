// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.cache.impl.id;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Helper to collect (ID, occurenceMask: {@link com.intellij.psi.search.UsageSearchContext}) pairs -- to be used in {@link IdIndexer} implementations.
 * Collects both case-sensitive and case-insensitive hashes at the same time.
 * Creates Map implementation heavily optimized for this specific purpose ({@link IdEntryToScopeMapImpl})
 * <p/>
 * All {@link IdIndexer} implementations are strongly recommended to use this class to collect IDs and occurrence masks, instead
 * of using some other Map implementation directly.
 *
 * @see IdIndex
 * @see com.intellij.psi.search.UsageSearchContext
 */
public final class IdDataConsumer {

  private final @NotNull IdEntryToScopeMapImpl hashToScopeMap = new IdEntryToScopeMapImpl();

  public @NotNull Map<IdIndexEntry, Integer> getResult() {
    //We serialise the data in he indexer's thread(s) to reduce load on the IndexWriter's thread -- which are <=1 per
    // index, while there are many more indexer threads. But indexer thread keeps an RA, which makes it vulnerable to
    // RA-by-WA cancellations & retries, which are both more probable AND more expensive the longer RA is.
    //So the tradeoff: serialise the data for reasonably small maps, and leave largest (but infrequent) maps to be
    // serialised later, in the IndexWriter.
    //Threshold 500 is chosen by observing Ultimate sourcetree indexing
    if(hashToScopeMap.size() < 500) {
      hashToScopeMap.ensureSerializedDataCached();
    }
    return hashToScopeMap;
  }

  public void addOccurrence(char[] chars, int start, int end, int occurrenceMask) {
    addOccurrence(null, chars, start, end, occurrenceMask);
  }

  public void addOccurrence(CharSequence charSequence, int start, int end, int occurrenceMask) {
    addOccurrence(charSequence, null, start, end, occurrenceMask);
  }

  private void addOccurrence(CharSequence charSequence, char[] chars, int start, int end, int occurrenceMask) {
    if (end == start || occurrenceMask == 0) return;
    //calculate both case-sensitive & case-insensitive hashes:
    int hash, hashNoCase;
    boolean hasArray = chars != null;
    if (IdIndexEntry.useStrongerHash()) {
      hash = hashNoCase = 0;
      boolean different = false;
      for (int off = start; off < end; off++) {
        char c = hasArray ? chars[off] : charSequence.charAt(off);
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
      char firstChar = hasArray ? chars[start] : charSequence.charAt(start);
      char lastChar = hasArray ? chars[end - 1] : charSequence.charAt(end - 1);
      hash = (firstChar << 8) + (lastChar << 4) + end - start;
      char firstCharLower = StringUtil.toLowerCase(firstChar);
      char lastCharLower = StringUtil.toLowerCase(lastChar);
      hashNoCase = (firstCharLower == firstChar && lastCharLower == lastChar) ? hash :
                   (firstCharLower << 8) + (lastCharLower << 4) + end - start;
    }
    hashToScopeMap.updateMask(hash, occurrenceMask);

    if (hashNoCase != hash) {
      hashToScopeMap.updateMask(hashNoCase, occurrenceMask);
    }
  }
}
