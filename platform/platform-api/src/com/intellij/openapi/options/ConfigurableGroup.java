// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

public interface ConfigurableGroup extends Configurable.Composite {
  @NlsContexts.ConfigurableName
  String getDisplayName();

  default @NlsContexts.DetailedDescription @Nullable String getDescription() { return null; }
}
