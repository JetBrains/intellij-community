// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementCreator;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.java.JpsJavaDependencyExtension;
import org.jetbrains.jps.model.java.JpsJavaDependencyScope;

@ApiStatus.Internal
public final class JpsJavaDependencyExtensionRole extends JpsElementChildRoleBase<JpsJavaDependencyExtension> implements JpsElementCreator<JpsJavaDependencyExtension> {
  public static final JpsJavaDependencyExtensionRole INSTANCE = new JpsJavaDependencyExtensionRole();

  private JpsJavaDependencyExtensionRole() {
    super("java dependency extension");
  }

  @Override
  public @NotNull JpsJavaDependencyExtensionImpl create() {
    return new JpsJavaDependencyExtensionImpl(false, JpsJavaDependencyScope.COMPILE);
  }
}
