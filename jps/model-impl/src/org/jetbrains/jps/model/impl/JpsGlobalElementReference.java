// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.ex.JpsElementReferenceBase;

@ApiStatus.Internal
public final class JpsGlobalElementReference extends JpsElementReferenceBase<JpsGlobalElementReference, JpsGlobal> {
  @Override
  public JpsGlobal resolve() {
    final JpsModel model = getModel();
    return model != null ? model.getGlobal() : null;
  }

  @Override
  public @NotNull JpsGlobalElementReference createCopy() {
    return new JpsGlobalElementReference();
  }

  @Override
  public String toString() {
    return "global ref";
  }
}
