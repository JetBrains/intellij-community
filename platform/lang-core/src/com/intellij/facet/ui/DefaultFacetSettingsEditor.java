// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.facet.ui;

import com.intellij.openapi.options.UnnamedConfigurable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public abstract class DefaultFacetSettingsEditor implements UnnamedConfigurable {
  public @Nullable @NonNls String getHelpTopic() {
    return null;
  }
}
