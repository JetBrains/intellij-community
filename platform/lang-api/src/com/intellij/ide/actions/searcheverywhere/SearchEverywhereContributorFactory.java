package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SearchEverywhereContributorFactory<Item> {

  @NotNull
  SearchEverywhereContributor<Item> createContributor(@NotNull AnActionEvent initEvent);
}
