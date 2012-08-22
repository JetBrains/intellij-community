package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;

/**
 * @author nik
 */
public abstract class JpsCompositeElementBase<Self extends JpsCompositeElementBase<Self>> extends JpsElementBase<Self> implements JpsCompositeElement {
  protected final JpsElementContainerImpl myContainer;

  protected JpsCompositeElementBase() {
    myContainer = new JpsElementContainerImpl(this);
  }

  protected JpsCompositeElementBase(JpsCompositeElementBase<Self> original) {
    myContainer = new JpsElementContainerImpl(original.myContainer, this);
  }

  public void applyChanges(@NotNull Self modified) {
    myContainer.applyChanges(modified.myContainer);
  }

  @Override
  @NotNull
  public JpsElementContainerImpl getContainer() {
    return myContainer;
  }
}
