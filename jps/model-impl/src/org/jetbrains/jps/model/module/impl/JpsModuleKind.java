package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementKind;
import org.jetbrains.jps.model.impl.JpsElementKindBase;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.impl.JpsElementCollectionKind;
import org.jetbrains.jps.model.module.JpsModuleListener;

/**
 * @author nik
 */
public class JpsModuleKind extends JpsElementKindBase<JpsModuleImpl> {
  public static final JpsElementKind<JpsModuleImpl> INSTANCE = new JpsModuleKind();
  public static final JpsElementCollectionKind<JpsModuleImpl> MODULE_COLLECTION_KIND = new JpsElementCollectionKind<JpsModuleImpl>(INSTANCE);

  public JpsModuleKind() {
    super("module");
  }

  @Override
  public void fireElementAdded(@NotNull JpsEventDispatcher dispatcher, @NotNull JpsModuleImpl element) {
    dispatcher.getPublisher(JpsModuleListener.class).moduleAdded(element);
  }

  @Override
  public void fireElementRemoved(@NotNull JpsEventDispatcher dispatcher, @NotNull JpsModuleImpl element) {
    dispatcher.getPublisher(JpsModuleListener.class).moduleRemoved(element);
  }
}
