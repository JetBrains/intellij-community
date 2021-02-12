// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus;

public interface ConfigurableGroup extends Configurable.Composite {
  @NlsContexts.ConfigurableName
  String getDisplayName();

  /**
   * @deprecated unused
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  default String getShortName() { return null; }
}
