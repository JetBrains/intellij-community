// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.CompletionSorter;
import com.intellij.codeInsight.lookup.CachingComparingClassifier;
import com.intellij.codeInsight.lookup.Classifier;
import com.intellij.codeInsight.lookup.ClassifierFactory;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class CompletionSorterImpl extends CompletionSorter {
  private final List<? extends ClassifierFactory<LookupElement>> myMembers;
  private final int myHashCode;

  @ApiStatus.Internal
  public CompletionSorterImpl(@NotNull List<? extends ClassifierFactory<LookupElement>> members) {
    myMembers = members;
    myHashCode = myMembers.hashCode();
  }

  public static @NotNull ClassifierFactory<LookupElement> weighingFactory(@NotNull LookupElementWeigher weigher) {
    String id = weigher.toString();
    return new ClassifierFactory<>(id) {
      @Override
      public @NotNull Classifier<LookupElement> createClassifier(@NotNull Classifier<LookupElement> next) {
        return new CachingComparingClassifier(next, weigher);
      }

      @Override
      public String toString() {
        return "Sorter " + id;
      }
    };
  }

  @Override
  public @NotNull CompletionSorterImpl weighBefore(@NotNull String beforeId, @NotNull LookupElementWeigher @NotNull ... weighers) {
    if (weighers.length == 0) return this;

    CompletionSorterImpl result = this;
    for (LookupElementWeigher weigher : weighers) {
      result = result.withClassifier(beforeId, true, weighingFactory(weigher));
    }
    return result;
  }

  @Override
  public @NotNull CompletionSorterImpl weighAfter(@NotNull String afterId, @NotNull LookupElementWeigher @NotNull ... weighers) {
    if (weighers.length == 0) return this;

    CompletionSorterImpl result = this;
    for (int i = weighers.length - 1; i >= 0; i--) {
      LookupElementWeigher weigher = weighers[i];
      result = result.withClassifier(afterId, false, weighingFactory(weigher));
    }
    return result;
  }

  @Override
  public @NotNull CompletionSorterImpl weigh(@NotNull LookupElementWeigher weigher) {
    return withClassifier(weighingFactory(weigher));
  }

  public @NotNull CompletionSorterImpl withClassifier(@NotNull ClassifierFactory<LookupElement> classifierFactory) {
    return enhanced(classifierFactory, myMembers.size());
  }

  public @NotNull CompletionSorterImpl withClassifier(@NotNull String anchorId,
                                                      boolean beforeAnchor,
                                                      @NotNull ClassifierFactory<LookupElement> classifierFactory) {
    int i = idIndex(anchorId);
    return enhanced(classifierFactory, beforeAnchor ? Math.max(0, i) : i + 1);
  }

  public @NotNull CompletionSorterImpl withoutClassifiers(@NotNull Predicate<? super ClassifierFactory<LookupElement>> removeCondition) {
    return new CompletionSorterImpl(ContainerUtil.filter(myMembers, t -> !removeCondition.test(t)));
  }

  /**
   * @return a copy of sorter with classifierFactory added to the specified index
   */
  private @NotNull CompletionSorterImpl enhanced(@NotNull ClassifierFactory<LookupElement> classifierFactory, int index) {
    List<ClassifierFactory<LookupElement>> copy = new ArrayList<>(myMembers);
    copy.add(index, classifierFactory);
    return new CompletionSorterImpl(copy);
  }

  private int idIndex(@NotNull String id) {
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

  /**
   * @return a function-style list of classifiers corresponding to {@code components} starting from {@code index}
   */
  private static @NotNull Classifier<LookupElement> createClassifier(int index,
                                                                     @NotNull List<? extends ClassifierFactory<LookupElement>> components,
                                                                     @NotNull Classifier<LookupElement> tail) {
    if (index == components.size()) {
      return tail;
    }

    return components.get(index).createClassifier(createClassifier(index + 1, components, tail));
  }

  public @NotNull Classifier<LookupElement> buildClassifier(@NotNull Classifier<LookupElement> tail) {
    return createClassifier(0, myMembers, tail);
  }
}
