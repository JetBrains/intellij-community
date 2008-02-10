package com.intellij.util.indexing;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 6, 2008
 */
public class IdDataConsumer {
  final Map<IdIndexEntry, Integer> myResult = new HashMap<IdIndexEntry, Integer>();

  public Map<IdIndexEntry, Integer> getResult() {
    return myResult;
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
      final IdIndexEntry key = new IdIndexEntry(hashcode);
      Integer v = myResult.get(key);
      if (v == null) {
        v = occurrenceMask;
      }
      else {
        v = v.intValue() | ((Integer)occurrenceMask).intValue();
      }
      myResult.put(key, v);
    }
  }
  
}
