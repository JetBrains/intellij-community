// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;

import java.util.Map;

@ApiStatus.Internal
public final class JpsEncodingConfigurationServiceImpl extends JpsEncodingConfigurationService {
  private static final JpsElementChildRoleBase<JpsSimpleElement<String>> ENCODING_ROLE = JpsElementChildRoleBase.create("encoding");

  @Override
  public @Nullable String getGlobalEncoding(@NotNull JpsGlobal global) {
    JpsSimpleElement<String> encoding = global.getContainer().getChild(ENCODING_ROLE);
    return encoding != null ? encoding.getData() : null;
  }

  @Override
  public void setGlobalEncoding(@NotNull JpsGlobal global, @Nullable String encoding) {
    if (encoding != null) {
      global.getContainer().setChild(ENCODING_ROLE, JpsElementFactory.getInstance().createSimpleElement(encoding));
    }
    else {
      global.getContainer().removeChild(ENCODING_ROLE);
    }
  }

  @Override
  public @Nullable String getProjectEncoding(@NotNull JpsModel model) {
    JpsEncodingProjectConfiguration configuration = getEncodingConfiguration(model.getProject());
    if (configuration != null) {
      String projectEncoding = configuration.getProjectEncoding();
      if (projectEncoding != null) {
        return projectEncoding;
      }
    }
    return getGlobalEncoding(model.getGlobal());
  }

  @Override
  public @Nullable JpsEncodingProjectConfiguration getEncodingConfiguration(@NotNull JpsProject project) {
    return project.getContainer().getChild(JpsEncodingProjectConfigurationImpl.ROLE);
  }

  @Override
  public @NotNull JpsEncodingProjectConfiguration setEncodingConfiguration(@NotNull JpsProject project,
                                                                           @Nullable String projectEncoding,
                                                                           @NotNull Map<String, String> urlToEncoding) {
    JpsEncodingProjectConfigurationImpl configuration = new JpsEncodingProjectConfigurationImpl(urlToEncoding, projectEncoding);
    return project.getContainer().setChild(JpsEncodingProjectConfigurationImpl.ROLE, configuration);
  }
}
