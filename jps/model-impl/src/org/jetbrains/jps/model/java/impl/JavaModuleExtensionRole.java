// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementCreator;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.java.JpsJavaModuleExtension;

public final class JavaModuleExtensionRole extends JpsElementChildRoleBase<JpsJavaModuleExtension> implements JpsElementCreator<JpsJavaModuleExtension> {
  public static final JavaModuleExtensionRole INSTANCE = new JavaModuleExtensionRole();

  private JavaModuleExtensionRole() {
    super("java module extension");
  }

  @NotNull
  @Override
  public JpsJavaModuleExtensionImpl create() {
    return new JpsJavaModuleExtensionImpl();
  }
}
