// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.CompletionSorter;
import com.intellij.codeInsight.lookup.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class CompletionSorterImpl extends CompletionSorter {
  private final List<? extends ClassifierFactory<LookupElement>> myMembers;
  private final int myHashCode;

  @ApiStatus.Internal
  public CompletionSorterImpl(List<? extends ClassifierFactory<LookupElement>> members) {
    myMembers = members;
    myHashCode = myMembers.hashCode();
  }

  public static ClassifierFactory<LookupElement> weighingFactory(final LookupElementWeigher weigher) {
    final String id = weigher.toString();
    return new ClassifierFactory<>(id) {
      @Override
      public Classifier<LookupElement> createClassifier(Classifier<LookupElement> next) {
        return new CachingComparingClassifier(next, weigher);
      }
    };
  }

  @Override public CompletionSorterImpl weighBefore(@NotNull final String beforeId, LookupElementWeigher... weighers) {
    if (weighers.length == 0) return this;

    CompletionSorterImpl result = this;
    for (LookupElementWeigher weigher : weighers) {
      result = result.withClassifier(beforeId, true, weighingFactory(weigher));
    }
    return result;
  }

  @Override public CompletionSorterImpl weighAfter(@NotNull final String afterId, LookupElementWeigher... weighers) {
    if (weighers.length == 0) return this;

    CompletionSorterImpl result = this;
    for (int i = weighers.length - 1; i >= 0; i--) {
      LookupElementWeigher weigher = weighers[i];
      result = result.withClassifier(afterId, false, weighingFactory(weigher));
    }
    return result;
  }

  @Override public CompletionSorterImpl weigh(final LookupElementWeigher weigher) {
    return withClassifier(weighingFactory(weigher));
  }

  public CompletionSorterImpl withClassifier(ClassifierFactory<LookupElement> classifierFactory) {
    return enhanced(classifierFactory, myMembers.size());
  }

  public CompletionSorterImpl withClassifier(@NotNull String anchorId,
                                             boolean beforeAnchor, ClassifierFactory<LookupElement> classifierFactory) {
    final int i = idIndex(anchorId);
    return enhanced(classifierFactory, beforeAnchor ? Math.max(0, i) : i + 1);
  }

  public CompletionSorterImpl withoutClassifiers(@NotNull Predicate<? super ClassifierFactory<LookupElement>> removeCondition) {
    return new CompletionSorterImpl(ContainerUtil.filter(myMembers, t -> !removeCondition.test(t)));
  }

  private CompletionSorterImpl enhanced(ClassifierFactory<LookupElement> classifierFactory, int index) {
    final List<ClassifierFactory<LookupElement>> copy = new ArrayList<>(myMembers);
    copy.add(index, classifierFactory);
    return new CompletionSorterImpl(copy);
  }


  private int idIndex(final String id) {
    return ContainerUtil.indexOf(myMembers, factory -> id.equals(factory.getId()));
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof CompletionSorterImpl that && myMembers.equals(that.myMembers);
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  private static Classifier<LookupElement> createClassifier(final int index,
                                                            final List<? extends ClassifierFactory<LookupElement>> components,
                                                            Classifier<LookupElement> tail) {
    if (index == components.size()) {
      return tail;
    }

    return components.get(index).createClassifier(createClassifier(index + 1, components, tail));
  }

  public Classifier<LookupElement> buildClassifier(Classifier<LookupElement> tail) {
    return createClassifier(0, myMembers, tail);
  }
}
