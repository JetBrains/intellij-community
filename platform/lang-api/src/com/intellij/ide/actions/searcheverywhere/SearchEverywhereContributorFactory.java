package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SearchEverywhereContributorFactory<T> {

  @NotNull
  SearchEverywhereContributor createContributor(AnActionEvent initEvent);

  @Nullable
  SearchEverywhereContributorFilter<T> createFilter();
}
