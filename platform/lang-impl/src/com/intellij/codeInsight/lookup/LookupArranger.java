/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * @author peter
 */
public abstract class LookupArranger {
  public static final LookupArranger DEFAULT = new LookupArranger() {

    @Override
    public Classifier<LookupElement> createRelevanceClassifier() {
      return ClassifierFactory.listClassifier();
    }
  };
  public static final LookupArranger LEXICOGRAPHIC = new LookupArranger() {

    @Override
    public Comparator<LookupElement> getItemComparator() {
      return new Comparator<LookupElement>() {
        @Override
        public int compare(LookupElement o1, LookupElement o2) {
          return o1.getLookupString().compareTo(o2.getLookupString());
        }
      };
    }

    @Override
    public Classifier<LookupElement> createRelevanceClassifier() {
      return ClassifierFactory.sortingListClassifier(getItemComparator());
    }
  };

  public void itemSelected(LookupElement item, final Lookup lookup) {
  }

  public int suggestPreselectedItem(List<LookupElement> sorted, Iterable<List<LookupElement>> groups) {
    return 0;
  }

  public abstract Classifier<LookupElement> createRelevanceClassifier();

  @Nullable
  public Comparator<LookupElement> getItemComparator() {
    return null; //don't sort
  }
}
