package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.impl.JpsElementCollectionKind;
import org.jetbrains.jps.model.impl.JpsElementKindBase;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsModuleSourceRootListener;

/**
 * @author nik
 */
public class JpsModuleSourceRootKind extends JpsElementKindBase<JpsModuleSourceRoot> {
  public static final JpsModuleSourceRootKind INSTANCE = new JpsModuleSourceRootKind();
  public static final JpsElementCollectionKind<JpsModuleSourceRoot> ROOT_COLLECTION_KIND = JpsElementCollectionKind.create(INSTANCE);

  public JpsModuleSourceRootKind() {
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
