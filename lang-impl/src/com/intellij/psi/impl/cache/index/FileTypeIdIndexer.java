package com.intellij.psi.impl.cache.index;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.IndexDataConsumer;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 16, 2008
 */
public abstract class FileTypeIdIndexer implements DataIndexer<IdIndexEntry, Integer, FileBasedIndex.FileContent> {

  public static void addOccurrence(IndexDataConsumer<IdIndexEntry, Integer> consumer, CharSequence charSequence, int start, int end, int occurrenceMask) {
    final int hashCode = StringUtil.stringHashCode(charSequence, start, end);
    addOccurrence(consumer, hashCode,occurrenceMask);
    final int hashCodeNoCase = StringUtil.stringHashCodeInsensitive(charSequence, start, end);
    if (hashCodeNoCase != hashCode) {
      addOccurrence(consumer, hashCodeNoCase,occurrenceMask);
    }
  }

  public static void addOccurrence(IndexDataConsumer<IdIndexEntry, Integer> consumer, char[] chars, int start, int end, int occurrenceMask) {
    final int hashCode = StringUtil.stringHashCode(chars, start, end);
    addOccurrence(consumer, hashCode,occurrenceMask);
    
    final int hashCodeNoCase = StringUtil.stringHashCodeInsensitive(chars, start, end);
    if (hashCodeNoCase != hashCode) {
      addOccurrence(consumer, hashCodeNoCase,occurrenceMask);
    }
  }

  public static void addOccurrence(IndexDataConsumer<IdIndexEntry, Integer> consumer, int hashcode, int occurrenceMask) {
    if (occurrenceMask != 0) {
      final IdIndexEntry indexEntry = new IdIndexEntry(hashcode);
      final int stop = (((int)UsageSearchContext.ANY) & 0xFF) + 1;
      for (int mask = 0x1; mask < stop; mask <<= 1) {
        if ((mask & occurrenceMask) != 0) {
          consumer.consume(indexEntry, mask);
        }
      }
    }
  }
  
}
