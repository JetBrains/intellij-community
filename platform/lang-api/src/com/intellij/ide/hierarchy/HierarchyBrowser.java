// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.hierarchy;

import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

/**
 * Base class for components which can be displayed in the Hierarchy toolwindow.
 */
public interface HierarchyBrowser extends PossiblyDumbAware {
  /**
   * Returns the UI component to be displayed in the toolwindow.
   *
   * @return the component to show.
   */
  JComponent getComponent();

  /**
   * Notifies the browser that it's being displayed in the specified content.
   *
   * @param content the content in which the browser is displayed.
   */
  void setContent(@NotNull Content content);
}
