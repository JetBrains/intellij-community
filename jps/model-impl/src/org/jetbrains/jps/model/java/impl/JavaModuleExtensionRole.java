// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementCreator;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.java.JpsJavaModuleExtension;

final class JavaModuleExtensionRole extends JpsElementChildRoleBase<JpsJavaModuleExtension> implements JpsElementCreator<JpsJavaModuleExtension> {
  public static final JavaModuleExtensionRole INSTANCE = new JavaModuleExtensionRole();

  private JavaModuleExtensionRole() {
    super("java module extension");
  }

  @Override
  public @NotNull JpsJavaModuleExtensionImpl create() {
    return new JpsJavaModuleExtensionImpl();
  }
}
