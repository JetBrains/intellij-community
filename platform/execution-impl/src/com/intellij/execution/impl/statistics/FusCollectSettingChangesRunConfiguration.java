// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl.statistics;

import com.intellij.execution.impl.SingleConfigurationConfigurable;
import org.jetbrains.annotations.NotNull;

public interface FusCollectSettingChangesRunConfiguration {

  /**
   * Allows collecting data on changes of Run Configuration settings when they are applied
   *
   * @param oldRunConfiguration run configuration without changes
   * @see SingleConfigurationConfigurable#apply()
   */
  void collectSettingChangesOnApply(@NotNull FusCollectSettingChangesRunConfiguration oldRunConfiguration);
}
