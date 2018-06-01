package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.actionSystem.AnActionEvent;

public interface SearchEverywhereContributorFactory {
  SearchEverywhereContributor createContributor(AnActionEvent initEvent);
}
