// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Provides icons which can be used to represent this IDE in different contexts.
 */
public interface ProductIcons {
  @NotNull
  static ProductIcons getInstance() {
    return ServiceManager.getService(ProductIcons.class);
  }

  /**
   * Returns icon which may be used to represent projects which may be opened by the IDE (e.g. in file choosers).
   */
  @NotNull
  Icon getProjectIcon();

  /**
   * Returns icon containing logo of this IDE.
   */
  @NotNull
  Icon getProductIcon();
}
