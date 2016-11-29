/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.indexing;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry;
import gnu.trove.THashMap;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntProcedure;

import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 6, 2008
 */
public class IdDataConsumer {
  private final TIntIntHashMap myResult = new TIntIntHashMap();

  public Map<IdIndexEntry, Integer> getResult() {
    final Map<IdIndexEntry, Integer> result = new THashMap<>(myResult.size());
    myResult.forEachEntry(new TIntIntProcedure() {
      @Override
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
