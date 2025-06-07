// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.ex.JpsElementBase;

final class JpsSimpleElementImpl<D> extends JpsElementBase<JpsSimpleElementImpl<D>> implements JpsSimpleElement<D> {
  private D myData;

  JpsSimpleElementImpl(D data) {
    myData = data;
  }

  private JpsSimpleElementImpl(JpsSimpleElementImpl<D> original) {
    myData = original.myData;
  }

  @Override
  public @NotNull D getData() {
    return myData;
  }

  @Override
  public void setData(@NotNull D data) {
    if (!myData.equals(data)) {
      myData = data;
    }
  }

  @Override
  public @NotNull JpsSimpleElementImpl<D> createCopy() {
    return new JpsSimpleElementImpl<>(this);
  }
}
