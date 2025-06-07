// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.JpsFileTypesConfiguration;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.serialization.JpsPathMapper;

@ApiStatus.Internal
public abstract class JpsGlobalBase extends JpsRootElementBase<JpsGlobalBase> implements JpsGlobal {
  private JpsPathMapper myPathMapper = JpsPathMapper.IDENTITY;

  protected JpsGlobalBase(@NotNull JpsModel model) { 
    super(model);
    myContainer.setChild(JpsFileTypesConfigurationImpl.ROLE, new JpsFileTypesConfigurationImpl());
  }

  @Override
  public @NotNull JpsFileTypesConfiguration getFileTypesConfiguration() {
    return myContainer.getChild(JpsFileTypesConfigurationImpl.ROLE);
  }

  @Override
  public @NotNull JpsPathMapper getPathMapper() {
    return myPathMapper;
  }

  @Override
  public void setPathMapper(@NotNull JpsPathMapper pathMapper) {
    myPathMapper = pathMapper;
  }

  @Override
  public @NotNull JpsElementReference<JpsGlobal> createReference() {
    return new JpsGlobalElementReference();
  }
}
