// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.ex;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsElementType;

/**
 * A base class for all implementations of {@link JpsElementType}.
 *
 * <p>
 * If elements of your type don't have any specific properties extend {@link JpsElementTypeWithDummyProperties} instead.
 * </p>
 */
public abstract class JpsElementTypeBase<P extends JpsElement> implements JpsElementType<P> {
  private final JpsElementChildRole<P> myPropertiesRole = new JpsElementChildRole<>();

  @Override
  public final @NotNull JpsElementChildRole<P> getPropertiesRole() {
    return myPropertiesRole;
  }
}
