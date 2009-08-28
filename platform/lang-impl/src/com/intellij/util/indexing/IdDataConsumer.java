package com.intellij.util.indexing;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntProcedure;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 6, 2008
 */
public class IdDataConsumer {
  private final TIntIntHashMap myResult = new TIntIntHashMap();

  public Map<IdIndexEntry, Integer> getResult() {
    final Map<IdIndexEntry, Integer> result = new HashMap<IdIndexEntry, Integer>();
    myResult.forEachEntry(new TIntIntProcedure() {
      public boolean execute(final int key, final int value) {
        result.put(new IdIndexEntry(key), value);
        return true;
      }
    });

    return result;
  }
  
  public void addOccurrence(CharSequence charSequence, int start, int end, int occurrenceMask) {
    final int hashCode = StringUtil.stringHashCode(charSequence, start, end);
    addOccurrence(hashCode,occurrenceMask);
    final int hashCodeNoCase = StringUtil.stringHashCodeInsensitive(charSequence, start, end);
    if (hashCodeNoCase != hashCode) {
      addOccurrence(hashCodeNoCase,occurrenceMask);
    }
  }

  public void addOccurrence(char[] chars, int start, int end, int occurrenceMask) {
    final int hashCode = StringUtil.stringHashCode(chars, start, end);
    addOccurrence(hashCode,occurrenceMask);
    
    final int hashCodeNoCase = StringUtil.stringHashCodeInsensitive(chars, start, end);
    if (hashCodeNoCase != hashCode) {
      addOccurrence(hashCodeNoCase,occurrenceMask);
    }
  }

  private void addOccurrence(int hashcode, int occurrenceMask) {
    if (occurrenceMask != 0) {
      final int old = myResult.get(hashcode);
      int v = old | occurrenceMask;
      if (v != old) {
        myResult.put(hashcode, v);
      }
    }
  }
}
