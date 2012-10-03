package org.jetbrains.jps.model.ex;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElementContainer;

/**
 * @author nik
 */
public abstract class JpsCompositeElementBase<Self extends JpsCompositeElementBase<Self>> extends JpsElementBase<Self> implements JpsCompositeElement {
  protected final JpsElementContainerEx myContainer;

  protected JpsCompositeElementBase() {
    myContainer = JpsExElementFactory.getInstance().createContainer(this);
  }

  protected JpsCompositeElementBase(JpsCompositeElementBase<Self> original) {
    myContainer = JpsExElementFactory.getInstance().createContainerCopy(original.myContainer, this);
  }

  public void applyChanges(@NotNull Self modified) {
    myContainer.applyChanges(modified.myContainer);
  }

  @Override
  @NotNull
  public JpsElementContainer getContainer() {
    return myContainer;
  }
}
