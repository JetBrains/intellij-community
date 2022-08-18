// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.content;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface ContentFactory {
  @NotNull
  Content createContent(@Nullable JComponent component, @Nullable @NlsContexts.TabTitle String displayName, boolean isLockable);

  @NotNull
  ContentManager createContentManager(@NotNull ContentUI contentUI, boolean canCloseContents, @NotNull Project project);

  /**
   * Uses {@link TabbedPaneContentUI}.
   *
   * @see #createContentManager(ContentUI, boolean, Project)
   */
  @NotNull
  ContentManager createContentManager(boolean canCloseContents, @NotNull Project project);

  static ContentFactory getInstance() {
    return ApplicationManager.getApplication().getService(ContentFactory.class);
  }

  /**
   * @deprecated use {@link ContentFactory#getInstance()} instead
   */
  @Deprecated(forRemoval = true)
  final class SERVICE {
    private SERVICE() {
    }

    public static ContentFactory getInstance() {
      return ContentFactory.getInstance();
    }
  }
}
