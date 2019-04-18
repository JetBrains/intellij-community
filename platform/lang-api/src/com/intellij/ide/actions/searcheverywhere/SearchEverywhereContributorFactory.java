package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SearchEverywhereContributorFactory<Item, Filter> {

  @NotNull
  SearchEverywhereContributor<Item, Filter> createContributor(@NotNull AnActionEvent initEvent);

  @Nullable
  SearchEverywhereContributorFilter<Filter> createFilter(@NotNull AnActionEvent initEvent);
}
