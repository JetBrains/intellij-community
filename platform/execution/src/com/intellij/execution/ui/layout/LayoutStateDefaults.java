// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.ui.layout;

import com.intellij.execution.ui.RunnerLayoutUi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Allows to configure layout default appearance.
 */
public interface LayoutStateDefaults {
  /**
   * Sets default tab text and icon for a tab with given {@code tabId}.
   * Default tab text will be used if it wasn't set explicitly for a tab, e.g. using
   * {@link com.intellij.ui.content.Content#setDisplayName(String)} for content associated with the tab.
   */
  @NotNull
  LayoutStateDefaults initTabDefaults(int tabId, @Nullable String defaultTabText, @Nullable Icon defaultTabIcon);

  default @NotNull LayoutStateDefaults initContentAttraction(@NotNull String contentId, @NotNull String condition) {
    return initContentAttraction(contentId, condition, new LayoutAttractionPolicy.FocusOnce());
  }

  /**
   * Schedules an attraction specified by {@code policy} for a content with {@code contentId}. The attraction will be
   * performed when {@code condition} happens.
   * This method also cancels all previously scheduled attractions associated with {@code condition}.
   * @param contentId String identifying a content
   *                 <ul>
   *                  <li>{@link RunnerLayoutUi#createContent(String, JComponent, String, Icon, JComponent)}</li>
   *                  <li>{@link com.intellij.execution.ui.ExecutionConsole#CONSOLE_CONTENT_ID}</li>
   *                 </ul>
   * @param condition String identifying a moment of time to perform the content attraction, e.g.
   *                  {@link LayoutViewOptions#STARTUP} on content UI showing
   * @param policy    LayoutAttractionPolicy instance
   * @return this
   */
  @NotNull
  LayoutStateDefaults initContentAttraction(@NotNull String contentId, @NotNull String condition, @NotNull LayoutAttractionPolicy policy);

  /**
   * Cancels attractions previously scheduled by {@link #initContentAttraction} to be performed
   * when {@code condition} happens.
   * @param condition String identifying a moment of time, e.g.
   *                  {@link LayoutViewOptions#STARTUP} on content UI showing
   * @return this
   */
  @NotNull
  LayoutStateDefaults cancelContentAttraction(@NotNull String condition);
}
