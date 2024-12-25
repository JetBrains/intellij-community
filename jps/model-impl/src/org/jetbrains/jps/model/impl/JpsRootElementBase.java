// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;

abstract class JpsRootElementBase<E extends JpsRootElementBase<E>> extends JpsCompositeElementBase<E> {
  private final JpsModel myModel;

  protected JpsRootElementBase(@NotNull JpsModel model) {
    super();
    myModel = model;
  }

  @Override
  public @NotNull JpsModel getModel() {
    return myModel;
  }

  @Override
  public @NotNull E createCopy() {
    throw new UnsupportedOperationException("'createCopy' not implemented in " + getClass().getName());
  }
}
