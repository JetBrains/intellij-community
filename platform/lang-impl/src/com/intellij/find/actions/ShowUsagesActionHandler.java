// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions;

import com.intellij.find.FindBundle;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.PopupAdvertisement;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageSearchPresentation;
import com.intellij.usages.UsageSearcher;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.usages.impl.UsageViewPopupManager;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ShowUsagesActionHandler {

  boolean isValid();

  @NotNull UsageSearchPresentation getPresentation();

  @NotNull UsageSearcher createUsageSearcher();

  void findUsages();

  @Nullable ShowUsagesActionHandler showDialog();

  @Nullable ShowUsagesActionHandler withScope(@NotNull SearchScope searchScope);

  default @Nullable ShowUsagesActionHandler withMaximalScope() {
    return withScope(getMaximalScope());
  }

  default boolean isSaveScope() {
    return true;
  }

  @Nullable ShowUsagesParameters moreUsages(@NotNull ShowUsagesParameters parameters);

  @NotNull SearchScope getSelectedScope();

  @NotNull SearchScope getMaximalScope();

  @Nullable Language getTargetLanguage();

  @NotNull Class<?> getTargetClass();

  @NotNull List<EventPair<?>> getEventData();

  default void afterOpen(@NotNull AbstractPopup popup) { }

  default void beforeClose(@NonNls String reason) { }

  boolean navigateToSingleUsageImmediately();

  @NotNull List<EventPair<?>> buildFinishEventData(@Nullable UsageInfo selectedUsage);

  default UsageViewImpl createUsageView(Project project) {
    return project.getService(UsageViewPopupManager.class).createUsageViewPopup(getTargetLanguage());
  }

  static @PopupAdvertisement @Nullable String getSecondInvocationHint(@NotNull ShowUsagesActionHandler actionHandler) {
    KeyboardShortcut shortcut = ShowUsagesAction.getShowUsagesShortcut();
    if (shortcut == null) {
      return null;
    }
    SearchScope maximalScope;
    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-349677, EA-847485")) {
      maximalScope = actionHandler.getMaximalScope();
    }
    if (maximalScope instanceof LocalSearchScope) {
      return null;
    }
    if (actionHandler.getSelectedScope().equals(maximalScope)) {
      return null;
    }
    return FindBundle.message("show.usages.advertisement", KeymapUtil.getShortcutText(shortcut), maximalScope.getDisplayName());
  }
}
