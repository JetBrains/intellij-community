package com.intellij.psi.impl.cache.index;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.cache.impl.idCache.IdTableBuilding;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.IndexDataConsumer;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 16, 2008
 */
public abstract class FileTypeIdIndexer implements DataIndexer<IdIndexEntry, Void, FileBasedIndex.FileContent> {

  public static void scanWords(final IndexDataConsumer<IdIndexEntry, Void> consumer,
                               final CharSequence chars,
                               final int start,
                               final int end,
                               final int occurrenceMask) {
    IdTableBuilding.scanWords(new IdTableBuilding.ScanWordProcessor(){
      public void run(final CharSequence chars, final int start, final int end, char[] charsArray) {
        if (charsArray != null) {
          addOccurrence(consumer, charsArray, start, end, occurrenceMask);
        } else {
          addOccurrence(consumer, chars, start, end, occurrenceMask);
        }
      }
    }, chars, start, end);
  }

  public static void addOccurrence(IndexDataConsumer<IdIndexEntry, Void> consumer, CharSequence charSequence, int occurrenceMask) {
    addOccurrence(consumer, StringUtil.stringHashCode(charSequence),occurrenceMask);
    addOccurrence(consumer, StringUtil.stringHashCodeInsensitive(charSequence),occurrenceMask);
  }
  
  public static void addOccurrence(IndexDataConsumer<IdIndexEntry, Void> consumer, CharSequence charSequence, int start, int end, int occurrenceMask) {
    addOccurrence(consumer, StringUtil.stringHashCode(charSequence, start, end),occurrenceMask);
    addOccurrence(consumer, StringUtil.stringHashCodeInsensitive(charSequence, start, end),occurrenceMask);
  }

  public static void addOccurrence(IndexDataConsumer<IdIndexEntry, Void> consumer, char[] chars, int start, int end, int occurrenceMask) {
    addOccurrence(consumer, StringUtil.stringHashCode(chars, start, end),occurrenceMask);
    addOccurrence(consumer, StringUtil.stringHashCodeInsensitive(chars, start, end),occurrenceMask);
  }

  public static void addOccurrence(IndexDataConsumer<IdIndexEntry, Void> consumer, int hashcode, int occurrenceMask) {
    if (occurrenceMask != 0) {
      final int stop = (((int)UsageSearchContext.ANY) & 0xFF) + 1;
      for (int mask = 0x1; mask < stop; mask <<= 1) {
        if ((mask & occurrenceMask) != 0) {
          consumer.consume(new IdIndexEntry(hashcode, mask), null);
        }
      }
    }
  }
  
}
