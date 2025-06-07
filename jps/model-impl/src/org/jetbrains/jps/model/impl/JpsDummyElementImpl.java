// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.ex.JpsElementBase;

final class JpsDummyElementImpl extends JpsElementBase<JpsDummyElementImpl> implements JpsDummyElement {
  @Override
  public @NotNull JpsDummyElementImpl createCopy() {
    return new JpsDummyElementImpl();
  }
}
