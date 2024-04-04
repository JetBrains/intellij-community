// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.cache.impl.id;

import com.intellij.openapi.util.text.StringUtil;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiConsumer;

public final class IdDataConsumer {
  private final @NotNull Int2IntMap myOccurrences = new Int2IntOpenHashMap();
  private final @NotNull Map<IdIndexEntry, Integer> myResult = new AbstractMap<>() {
    @Override
    public int size() {
      return myOccurrences.size();
    }

    @Override
    public boolean containsKey(Object key) {
      return key instanceof IdIndexEntry entry && myOccurrences.containsKey(entry.getWordHashCode());
    }

    @Override
    public Integer get(Object key) {
      //noinspection deprecation
      return key instanceof IdIndexEntry entry ? myOccurrences.get((Object)entry.getWordHashCode()) : null;
    }

    @NotNull
    @Override
    public Set<IdIndexEntry> keySet() {
      return new AbstractSet<>() {
        @Override
        public boolean contains(Object o) {
          return o instanceof IdIndexEntry entry && myOccurrences.containsKey(entry.getWordHashCode());
        }

        @Override
        public Iterator<IdIndexEntry> iterator() {
          return new Iterator<>() {
            final IntIterator myIterator = myOccurrences.keySet().iterator();

            @Override
            public boolean hasNext() {
              return myIterator.hasNext();
            }

            @Override
            public IdIndexEntry next() {
              return new IdIndexEntry(myIterator.nextInt());
            }
          };
        }

        @Override
        public int size() {
          return myOccurrences.size();
        }
      };
    }

    @NotNull
    @Override
    public Collection<Integer> values() {
      return myOccurrences.values();
    }

    @NotNull
    @Override
    public Set<Entry<IdIndexEntry, Integer>> entrySet() {
      return new AbstractSet<>() {
        @Override
        public Iterator<Entry<IdIndexEntry, Integer>> iterator() {
          return new Iterator<>() {
            final ObjectIterator<Int2IntMap.Entry> myIterator = myOccurrences.int2IntEntrySet().iterator();

            @Override
            public boolean hasNext() {
              return myIterator.hasNext();
            }

            @Override
            public Entry<IdIndexEntry, Integer> next() {
              Int2IntMap.Entry entry = myIterator.next();
              return Map.entry(new IdIndexEntry(entry.getIntKey()), entry.getIntValue());
            }
          };
        }

        @Override
        public int size() {
          return myOccurrences.size();
        }
      };
    }

    @Override
    public void forEach(BiConsumer<? super IdIndexEntry, ? super Integer> consumer) {
      myOccurrences.forEach((hash, value) -> consumer.accept(new IdIndexEntry(hash), value));
    }
  };

  public @NotNull Map<IdIndexEntry, Integer> getResult() {
    return myResult;
  }

  public void addOccurrence(char[] chars, int start, int end, int occurrenceMask) {
    addOccurrence(null, chars, start, end, occurrenceMask);
  }

  public void addOccurrence(CharSequence charSequence, int start, int end, int occurrenceMask) {
    addOccurrence(charSequence, null, start, end, occurrenceMask);
  }

  private void addOccurrence(CharSequence charSequence, char[] chars, int start, int end, int occurrenceMask) {
    if (end == start || occurrenceMask == 0) return;
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
    myOccurrences.mergeInt(hash, occurrenceMask, (prev, cur) -> prev | cur);

    if (hashNoCase != hash) {
      myOccurrences.mergeInt(hashNoCase, occurrenceMask, (prev, cur) -> prev | cur);
    }
  }
}
