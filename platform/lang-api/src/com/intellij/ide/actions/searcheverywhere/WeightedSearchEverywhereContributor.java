// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public interface WeightedSearchEverywhereContributor<I> extends SearchEverywhereContributor<I> {

  void fetchWeightedElements(@NotNull String pattern,
                             @NotNull ProgressIndicator progressIndicator,
                             @NotNull Processor<? super FoundItemDescriptor<I>> consumer);

  @Override
  default void fetchElements(@NotNull String pattern,
                             @NotNull ProgressIndicator progressIndicator,
                             @NotNull Processor<? super I> consumer) {
    fetchWeightedElements(pattern, progressIndicator, descriptor -> consumer.process(descriptor.getItem()));
  }

  @NotNull
  default ContributorSearchResult<? super FoundItemDescriptor<I>> searchWeightedElements(@NotNull String pattern,
                                                                                         @NotNull ProgressIndicator progressIndicator,
                                                                                         int elementsLimit) {
    ContributorSearchResult.Builder<? super FoundItemDescriptor<I>> builder = ContributorSearchResult.builder();
    fetchWeightedElements(pattern, progressIndicator, descriptor -> {
      if (elementsLimit < 0 || builder.itemsCount() < elementsLimit) {
        builder.addItem(descriptor);
        return true;
      }
      else {
        builder.setHasMore(true);
        return false;
      }
    });

    return builder.build();
  }

  @NotNull
  default List<? super FoundItemDescriptor<I>> searchWeightedElements(@NotNull String pattern,
                                                                      @NotNull ProgressIndicator progressIndicator) {
    List<? super FoundItemDescriptor<I>> res = new ArrayList<>();
    fetchWeightedElements(pattern, progressIndicator, res::add);
    return res;
  }
}
