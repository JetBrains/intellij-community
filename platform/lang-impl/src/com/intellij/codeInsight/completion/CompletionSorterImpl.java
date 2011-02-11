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
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class CompletionSorterImpl extends CompletionSorter {
  private final ClassifyingSequencer mySequencer;

  private CompletionSorterImpl(ClassifyingSequencer sequencer) {
    mySequencer = sequencer;
  }

  public static CompletionSorter defaultSorter() {
    final ClassifyingSequencer sequencer = new ClassifyingSequencer(); //todo
    return new CompletionSorterImpl(sequencer);
  }

  public static CompletionSorter emptySorter() {
    return new CompletionSorterImpl(new ClassifyingSequencer());
  }

  private static ClassifierFactory<LookupElement> weighingFactory(final LookupElementWeigher weigher) {
    return new ClassifierFactory<LookupElement>(weigher.getClass().getName()) {
      @Override
      public Classifier<LookupElement> createClassifier(Classifier<LookupElement> next) {
        return new ComparingClassifier<LookupElement>(next) {
          @Override
          public Comparable getWeight(LookupElement element) {
            return weigher.weigh(element);
          }
        };
      }
    };
  }

  @Override public CompletionSorter weighBefore(@NotNull final String beforeId, final LookupElementWeigher weigher) {
    return withClassifier(beforeId, true, weighingFactory(weigher));
  }

  @Override public CompletionSorter weighAfter(@NotNull final String afterId, final LookupElementWeigher weigher) {
    return withClassifier(afterId, false, weighingFactory(weigher));
  }

  @Override public CompletionSorter weigh(final LookupElementWeigher weigher) {
    return withClassifier(weighingFactory(weigher));
  }

  public CompletionSorter withClassifier(ClassifierFactory<LookupElement> factory) {
    return new CompletionSorterImpl(mySequencer.withClassifier(factory));
  }

  public CompletionSorter withClassifier(@NotNull String anchorId, boolean beforeAnchor, ClassifierFactory<LookupElement> factory) {
    return new CompletionSorterImpl(mySequencer.withClassifier(factory, anchorId, beforeAnchor));
  }
}
