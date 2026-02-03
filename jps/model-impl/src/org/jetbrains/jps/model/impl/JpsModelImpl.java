// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.JpsModel;

@ApiStatus.Internal
public final class JpsModelImpl implements JpsModel {
  private final JpsProjectImpl myProject;
  private final JpsGlobalImpl myGlobal;

  public JpsModelImpl() {
    myProject = new JpsProjectImpl(this);
    myGlobal = new JpsGlobalImpl(this);
  }

  @Override
  public @NotNull JpsProjectImpl getProject() {
    return myProject;
  }

  @Override
  public @NotNull JpsGlobalImpl getGlobal() {
    return myGlobal;
  }

  @Override
  public void registerExternalReference(@NotNull JpsElementReference<?> reference) {
    myProject.addExternalReference(reference);
  }
}
