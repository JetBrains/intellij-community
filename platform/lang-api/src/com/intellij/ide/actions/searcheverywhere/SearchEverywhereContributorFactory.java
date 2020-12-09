package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public interface SearchEverywhereContributorFactory<Item> {

  @NotNull
  SearchEverywhereContributor<Item> createContributor(@NotNull AnActionEvent initEvent);

  @NotNull
  default SearchEverywhereTabDescriptor getTab() {
    return SearchEverywhereTabDescriptor.PROJECT;
  }
}
