// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  final class SERVICE {
    private SERVICE() {
    }

    public static ContentFactory getInstance() {
      return ApplicationManager.getApplication().getService(ContentFactory.class);
    }
  }
}
