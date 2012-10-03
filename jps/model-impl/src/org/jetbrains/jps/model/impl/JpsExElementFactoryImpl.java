package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsElementCollection;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;
import org.jetbrains.jps.model.ex.JpsElementContainerEx;
import org.jetbrains.jps.model.ex.JpsElementContainerImpl;
import org.jetbrains.jps.model.ex.JpsExElementFactory;

/**
 * @author nik
 */
public class JpsExElementFactoryImpl extends JpsExElementFactory {
  @Override
  public JpsElementContainerEx createContainer(@NotNull JpsCompositeElementBase<?> parent) {
    return new JpsElementContainerImpl(parent);
  }

  @Override
  public JpsElementContainerEx createContainerCopy(@NotNull JpsElementContainerEx original,
                                                            @NotNull JpsCompositeElementBase<?> parent) {
    return new JpsElementContainerImpl(original, parent);
  }

  @Override
  public <E extends JpsElement> JpsElementCollection<E> createCollection(JpsElementChildRole<E> role) {
    return new JpsElementCollectionImpl<E>(role);
  }
}
