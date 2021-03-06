// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.ActionListener;
import java.util.function.Supplier;

public interface BrowsableTargetEnvironmentType {

  @NotNull <T extends Component> ActionListener createBrowser(@NotNull Project project,
                                                              @NlsContexts.DialogTitle String title,
                                                              @NotNull TextComponentAccessor<T> textComponentAccessor,
                                                              @NotNull T component,
                                                              @NotNull Supplier<TargetEnvironmentConfiguration> configurationSupplier);

  /**
   * When configurable contains both connection parameters and components using them (text fields with browsing in this case),
   * those components need to have current connection settings available, not the last applied to with [Configurable.apply].
   *
   * This interface displays ability and provides API to get connection settings, which are shown in UI. See IDEA-255466.
   */
  interface ConfigurableCurrentConfigurationProvider {
    @NotNull
    TargetEnvironmentConfiguration getCurrentConfiguration();
  }
}
