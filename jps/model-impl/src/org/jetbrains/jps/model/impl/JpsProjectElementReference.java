// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.ex.JpsElementReferenceBase;

@ApiStatus.Internal
public final class JpsProjectElementReference extends JpsElementReferenceBase<JpsProjectElementReference, JpsProject> {
  @Override
  public JpsProject resolve() {
    final JpsModel model = getModel();
    return model != null ? model.getProject() : null;
  }

  @Override
  public @NotNull JpsProjectElementReference createCopy() {
    return new JpsProjectElementReference();
  }

  @Override
  public String toString() {
    return "project ref";
  }
}
