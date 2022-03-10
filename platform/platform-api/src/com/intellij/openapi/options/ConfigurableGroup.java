// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.openapi.util.NlsContexts;

public interface ConfigurableGroup extends Configurable.Composite {
  @NlsContexts.ConfigurableName
  String getDisplayName();

  /**
   * @deprecated unused
   */
  @Deprecated(forRemoval = true)
  default String getShortName() { return null; }
}
