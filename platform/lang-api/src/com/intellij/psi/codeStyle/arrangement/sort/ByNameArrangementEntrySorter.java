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
package com.intellij.psi.codeStyle.arrangement.sort;

import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.NameAwareArrangementEntry;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 7/19/12 6:17 PM
 */
public class ByNameArrangementEntrySorter implements ArrangementEntrySorter {

  @Override
  public void sort(@NotNull List<? extends ArrangementEntry> entries) {
    TObjectIntHashMap<ArrangementEntry> weights = new TObjectIntHashMap<ArrangementEntry>();
    for (int i = 0; i < entries.size(); i++) {
      weights.put(entries.get(i), i);
    }
    Collections.sort(entries, new MyComparator(weights));
  }
  
  private static class MyComparator implements Comparator<ArrangementEntry> {
    
    @NotNull
    private final TObjectIntHashMap<ArrangementEntry> myFallbackWeights;

    MyComparator(@NotNull TObjectIntHashMap<ArrangementEntry> weights) {
      myFallbackWeights = weights;
    }

    @Override
    public int compare(ArrangementEntry o1, ArrangementEntry o2) {
      if (o1 instanceof NameAwareArrangementEntry && o2 instanceof NameAwareArrangementEntry) {
        String name1 = ((NameAwareArrangementEntry)o1).getName(); 
        String name2 = ((NameAwareArrangementEntry)o1).getName();
        if (name1 != null && name2 != null) {
          return name1.compareTo(name2);
        }
      }
      return myFallbackWeights.get(o1) - myFallbackWeights.get(o2);
    }
  } 
}
