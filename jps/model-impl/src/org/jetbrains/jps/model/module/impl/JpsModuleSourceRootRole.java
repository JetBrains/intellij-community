package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.ex.JpsElementCollectionRole;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsModuleSourceRootListener;

/**
 * @author nik
 */
public class JpsModuleSourceRootRole extends JpsElementChildRoleBase<JpsModuleSourceRoot> {
  public static final JpsModuleSourceRootRole INSTANCE = new JpsModuleSourceRootRole();
  public static final JpsElementCollectionRole<JpsModuleSourceRoot> ROOT_COLLECTION_ROLE = JpsElementCollectionRole.create(INSTANCE);

  public JpsModuleSourceRootRole() {
    super("module source root");
  }

  @Override
  public void fireElementAdded(@NotNull JpsEventDispatcher dispatcher, @NotNull JpsModuleSourceRoot element) {
    dispatcher.getPublisher(JpsModuleSourceRootListener.class).sourceRootAdded(element);
  }

  @Override
  public void fireElementRemoved(@NotNull JpsEventDispatcher dispatcher, @NotNull JpsModuleSourceRoot element) {
    dispatcher.getPublisher(JpsModuleSourceRootListener.class).sourceRootRemoved(element);
  }
}
