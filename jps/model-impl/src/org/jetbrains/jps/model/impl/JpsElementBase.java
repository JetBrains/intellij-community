package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;

/**
 * @author nik
 */
public abstract class JpsElementBase<Self extends JpsElementBase<Self>> implements JpsElement, JpsElement.BulkModificationSupport<Self> {
  private JpsEventDispatcher myEventDispatcher;
  protected JpsParentElement myParent;

  protected JpsElementBase(JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    myEventDispatcher = eventDispatcher;
    myParent = parent;
  }

  public JpsElementBase(JpsElementBase original, JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    myEventDispatcher = eventDispatcher;
    myParent = parent;
  }

  protected JpsEventDispatcher getEventDispatcher() {
    return myEventDispatcher;
  }

  @NotNull
  @Override
  public BulkModificationSupport<?> getBulkModificationSupport() {
    return this;
  }

  @NotNull
  public abstract Self createCopy(@NotNull JpsModel model, @NotNull JpsEventDispatcher eventDispatcher, JpsParentElement parent);

  public abstract void applyChanges(@NotNull Self modified);

  public JpsParentElement getParent() {
    return myParent;
  }
}
