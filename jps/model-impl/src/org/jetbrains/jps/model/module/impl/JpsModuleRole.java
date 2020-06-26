// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.ex.JpsElementCollectionRole;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleListener;

public final class JpsModuleRole extends JpsElementChildRoleBase<JpsModule> {
  private static final JpsElementChildRole<JpsModule> INSTANCE = new JpsModuleRole();
  public static final JpsElementCollectionRole<JpsModule> MODULE_COLLECTION_ROLE = JpsElementCollectionRole.create(INSTANCE);

  private JpsModuleRole() {
    super("module");
  }

  @Override
  public void fireElementAdded(@NotNull JpsEventDispatcher dispatcher, @NotNull JpsModule element) {
    dispatcher.getPublisher(JpsModuleListener.class).moduleAdded(element);
  }

  @Override
  public void fireElementRemoved(@NotNull JpsEventDispatcher dispatcher, @NotNull JpsModule element) {
    dispatcher.getPublisher(JpsModuleListener.class).moduleRemoved(element);
  }
}
