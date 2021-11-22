// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet.ui;

import com.intellij.openapi.options.UnnamedConfigurable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

public abstract class DefaultFacetSettingsEditor implements UnnamedConfigurable {
  @Nullable @NonNls
  public String getHelpTopic() {
    return null;
  }
}
