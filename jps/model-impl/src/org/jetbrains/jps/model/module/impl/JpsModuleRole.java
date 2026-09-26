// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.module.impl;

import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.ex.JpsNamedElementCollectionRole;
import org.jetbrains.jps.model.module.JpsModule;

public final class JpsModuleRole extends JpsElementChildRoleBase<JpsModule> {
  private static final JpsElementChildRole<JpsModule> INSTANCE = new JpsModuleRole();
  public static final JpsNamedElementCollectionRole<JpsModule> MODULE_COLLECTION_ROLE = JpsNamedElementCollectionRole.create(INSTANCE);

  private JpsModuleRole() {
    super("module");
  }
}
