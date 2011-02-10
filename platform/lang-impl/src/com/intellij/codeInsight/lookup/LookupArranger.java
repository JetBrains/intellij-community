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

import com.intellij.openapi.util.NotNullFactory;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * @author peter
 */
public abstract class LookupArranger {
  public static final LookupArranger DEFAULT = new LookupArranger() {
    @Override
    public Comparable getRelevance(LookupElement element) {
      return 0;
    }

  };
  public static final LookupArranger LEXICOGRAPHIC = new LookupArranger() {
    @Override
    public Comparable getRelevance(LookupElement element) {
      return 0;
    }

    @Override
    public Comparator<LookupElement> getItemComparator() {
      return new Comparator<LookupElement>() {
        @Override
        public int compare(LookupElement o1, LookupElement o2) {
          return o1.getLookupString().compareTo(o2.getLookupString());
        }
      };
    }
  };

  public abstract Comparable getRelevance(LookupElement element);

  public void itemSelected(LookupElement item, final Lookup lookup) {
  }

  public int suggestPreselectedItem(List<LookupElement> sorted) {
    return 0;
  }

  public Classifier<LookupElement> createRelevanceClassifier() {
    final Comparator<LookupElement> c = getItemComparator();
    final NotNullFactory<Classifier<LookupElement>> next = c != null ? ClassifierFactory.sortingListClassifier(c) : ClassifierFactory.<LookupElement>listClassifier();
    return new ComparingClassifier<LookupElement>(next) {
      @Override
      public Comparable getWeight(LookupElement element) {
        return getRelevance(element);
      }
    };
  }

  @Nullable
  public Comparator<LookupElement> getItemComparator() {
    return null; //don't sort
  }
}
