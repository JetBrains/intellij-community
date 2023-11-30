// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Provides icons which can be used to represent this IDE in different contexts.
 */
public interface ProductIcons {
  static @NotNull ProductIcons getInstance() {
    return ApplicationManager.getApplication().getService(ProductIcons.class);
  }

  /**
   * Returns a node icon to represent projects which may be opened by the IDE (e.g. in file choosers).
   */
  @NotNull
  Icon getProjectNodeIcon();

  /**
   * Returns an action icon to represent a project directory.
   */
  @NotNull
  Icon getProjectIcon();

  /**
   * Returns icon containing logo of this IDE.
   */
  @NotNull
  Icon getProductIcon();
}
