package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementKind;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.impl.JpsElementCollectionKind;
import org.jetbrains.jps.model.impl.JpsElementKindBase;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleListener;

/**
 * @author nik
 */
public class JpsModuleKind extends JpsElementKindBase<JpsModule> {
  public static final JpsElementKind<JpsModule> INSTANCE = new JpsModuleKind();
  public static final JpsElementCollectionKind<JpsModule> MODULE_COLLECTION_KIND = JpsElementCollectionKind.create(INSTANCE);

  public JpsModuleKind() {
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
