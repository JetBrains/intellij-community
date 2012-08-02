package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.impl.JpsElementCollectionRole;
import org.jetbrains.jps.model.impl.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleListener;

/**
 * @author nik
 */
public class JpsModuleRole extends JpsElementChildRoleBase<JpsModule> {
  public static final JpsElementChildRole<JpsModule> INSTANCE = new JpsModuleRole();
  public static final JpsElementCollectionRole<JpsModule> MODULE_COLLECTION_ROLE = JpsElementCollectionRole.create(INSTANCE);

  public JpsModuleRole() {
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
