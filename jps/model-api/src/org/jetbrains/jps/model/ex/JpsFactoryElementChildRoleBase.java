// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.ex;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsElementCreator;

@ApiStatus.Internal
public class JpsFactoryElementChildRoleBase<E extends JpsElement> extends JpsElementChildRole<E> implements JpsElementCreator<E> {
  private final String myDebugName;
  private final JpsElementCreator<E> myFactoryImpl;

  protected JpsFactoryElementChildRoleBase(String debugName, JpsElementCreator<E> factoryImpl) {
    myDebugName = debugName;
    myFactoryImpl = factoryImpl;
  }

  @Override
  public String toString() {
    return myDebugName;
  }

  @Override
  public @NotNull E create() {
    return myFactoryImpl.create();
  }

  public static <E extends JpsElement> JpsFactoryElementChildRoleBase<E> create(String debugName, JpsElementCreator<E> factory) {
    return new JpsFactoryElementChildRoleBase<>(debugName, factory);
  }
}
