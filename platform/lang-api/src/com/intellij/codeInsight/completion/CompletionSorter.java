/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.NotNullFactory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * @author peter
 */
public class CompletionSorter {
  private final ClassifyingSequencer mySequencer;

  private CompletionSorter(ClassifyingSequencer sequencer) {
    mySequencer = sequencer;
  }

  public static CompletionSorter defaultSorter() {
    final ClassifyingSequencer sequencer = new ClassifyingSequencer(); //todo
    return new CompletionSorter(sequencer);
  }

  public static CompletionSorter emptySorter() {
    return new CompletionSorter(new ClassifyingSequencer());
  }

  private static ClassifierFactory<LookupElement> weighingFactory(final LookupElementWeigher weigher) {
    return new ClassifierFactory<LookupElement>(weigher.toString()) {
      @Override
      public Classifier<LookupElement> createClassifier(NotNullFactory<Classifier<LookupElement>> next) {
        return new ComparingClassifier<LookupElement>(next) {
          @Override
          Comparable getWeight(LookupElement o) {
            return weigher.weigh(o);
          }
        };
      }
    };
  }

  public CompletionSorter weighBefore(@NotNull final String beforeId, final LookupElementWeigher weigher) {
    return withClassifier(beforeId, true, weighingFactory(weigher));
  }

  public CompletionSorter weighAfter(@NotNull final String afterId, final LookupElementWeigher weigher) {
    return withClassifier(afterId, false, weighingFactory(weigher));
  }

  public CompletionSorter weigh(final LookupElementWeigher weigher) {
    return withClassifier(weighingFactory(weigher));
  }

  public CompletionSorter withClassifier(ClassifierFactory<LookupElement> factory) {
    return new CompletionSorter(mySequencer.withClassifier(factory));
  }

  public CompletionSorter withClassifier(@NotNull String anchorId, boolean beforeAnchor, ClassifierFactory<LookupElement> factory) {
    return new CompletionSorter(mySequencer.withClassifier(factory, anchorId, beforeAnchor));
  }

  private static abstract class ComparingClassifier<T> implements Classifier<T> {
    private final SortedMap<Comparable, Classifier<T>> myWeightMap = new TreeMap<Comparable, Classifier<T>>();
    private final Factory<Classifier<T>> myContinuation;

    protected ComparingClassifier(Factory<Classifier<T>> next) {
      this.myContinuation = next;
    }

    abstract Comparable getWeight(T t);

    @Override
    public void addElement(T t) {
      final Comparable weight = getWeight(t);
      Classifier<T> next = myWeightMap.get(weight);
      if (next == null) {
        myWeightMap.put(weight, next = myContinuation.create());
      }
      next.addElement(t);
    }

    @Override
    public List<List<T>> classifyContents() {
      List<List<T>> result = new ArrayList<List<T>>();
      for (Classifier<T> classifier : myWeightMap.values()) {
        result.addAll(classifier.classifyContents());
      }
      return result;
    }
  }


}
