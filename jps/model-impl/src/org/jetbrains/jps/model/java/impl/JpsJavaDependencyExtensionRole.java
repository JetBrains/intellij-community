// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementCreator;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.java.JpsJavaDependencyExtension;
import org.jetbrains.jps.model.java.JpsJavaDependencyScope;

public final class JpsJavaDependencyExtensionRole extends JpsElementChildRoleBase<JpsJavaDependencyExtension> implements JpsElementCreator<JpsJavaDependencyExtension> {
  public static final JpsJavaDependencyExtensionRole INSTANCE = new JpsJavaDependencyExtensionRole();

  private JpsJavaDependencyExtensionRole() {
    super("java dependency extension");
  }

  @NotNull
  @Override
  public JpsJavaDependencyExtensionImpl create() {
    return new JpsJavaDependencyExtensionImpl(false, JpsJavaDependencyScope.COMPILE);
  }
}
