// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.schemes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface SchemePanelCustomizer {
  /**
   * @param abstractSchemePanel main settings scheme panel
   * @return the banner control which will be inserted on the top of the scheme panel
   */
  @Nullable
  JPanel getBannerToInsert(@NotNull JPanel abstractSchemePanel);
}
