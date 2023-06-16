// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface ServiceViewToolWindowDescriptor {
  @NotNull String getToolWindowId();

  @NotNull Icon getToolWindowIcon();

  @NotNull @NlsContexts.TabTitle String getStripeTitle();

  default boolean isExcludedByDefault() {
    return false;
  }

  default boolean isExclusionAllowed() {
    return true;
  }
}
