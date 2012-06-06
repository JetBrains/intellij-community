package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;

/**
 * @author nik
 */
public abstract class JpsCompositeElementBase<Self extends JpsCompositeElementBase<Self>> extends JpsElementBase<Self> implements JpsCompositeElement {
  protected final JpsElementContainerImpl myContainer;
  protected final JpsModel myModel;

  protected JpsCompositeElementBase(JpsModel model, JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    super(eventDispatcher, parent);
    myModel = model;
    myContainer = new JpsElementContainerImpl(model, eventDispatcher, this);
  }

  protected JpsCompositeElementBase(JpsCompositeElementBase<Self> original, JpsModel model, JpsEventDispatcher dispatcher,
                                    JpsParentElement parent) {
    super(original, dispatcher, parent);
    myModel = model;
    myContainer = new JpsElementContainerImpl(original.myContainer, model, dispatcher, this);
  }

  public void applyChanges(@NotNull Self element) {
    myContainer.applyChanges(element.myContainer);
  }

  @Override
  @NotNull
  public JpsElementContainerImpl getContainer() {
    return myContainer;
  }
}
