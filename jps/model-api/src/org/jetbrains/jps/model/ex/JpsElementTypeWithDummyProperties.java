// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.ex;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsElementTypeWithDefaultProperties;

/**
 * A base class for type elements without any specific properties
 */
public abstract class JpsElementTypeWithDummyProperties extends JpsElementTypeBase<JpsDummyElement> implements JpsElementTypeWithDefaultProperties<JpsDummyElement> {
  @Override
  public @NotNull JpsDummyElement createDefaultProperties() {
    return JpsElementFactory.getInstance().createDummyElement();
  }
}
