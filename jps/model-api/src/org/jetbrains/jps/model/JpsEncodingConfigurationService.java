// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.Map;

public abstract class JpsEncodingConfigurationService {
  public static JpsEncodingConfigurationService getInstance() {
    return JpsServiceManager.getInstance().getService(JpsEncodingConfigurationService.class);
  }

  public abstract @Nullable String getGlobalEncoding(@NotNull JpsGlobal global);

  @ApiStatus.Internal
  public abstract void setGlobalEncoding(@NotNull JpsGlobal global, @Nullable String encoding);

  public abstract @Nullable String getProjectEncoding(@NotNull JpsModel model);

  public abstract @Nullable JpsEncodingProjectConfiguration getEncodingConfiguration(@NotNull JpsProject project);

  @ApiStatus.Internal
  public abstract @NotNull JpsEncodingProjectConfiguration setEncodingConfiguration(@NotNull JpsProject project, @Nullable String projectEncoding, @NotNull Map<String, String> urlToEncoding);
}
