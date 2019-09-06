// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.content;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface ContentFactory {

  @NotNull
  Content createContent(JComponent component, @Nls(capitalization = Nls.Capitalization.Title) String displayName, boolean isLockable);

  @NotNull
  ContentManager createContentManager(@NotNull ContentUI contentUI, boolean canCloseContents, @NotNull Project project);

  /**
   * Uses {@link TabbedPaneContentUI}.
   *
   * @see #createContentManager(ContentUI, boolean, Project)
   */
  @NotNull
  ContentManager createContentManager(boolean canCloseContents, @NotNull Project project);

  class SERVICE {
    private SERVICE() {
    }

    public static ContentFactory getInstance() {
      return ServiceManager.getService(ContentFactory.class);
    }
  }
}
