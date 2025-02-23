// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;

@ApiStatus.Internal
public abstract class JpsRootElementBase<E extends JpsRootElementBase<E>> extends JpsCompositeElementBase<E> {
  private final JpsModel myModel;

  protected JpsRootElementBase(@NotNull JpsModel model) {
    super();
    myModel = model;
  }

  @Override
  public final @NotNull JpsModel getModel() {
    return myModel;
  }

  @Override
  public final @NotNull E createCopy() {
    throw new UnsupportedOperationException("'createCopy' not implemented in " + getClass().getName());
  }
}
