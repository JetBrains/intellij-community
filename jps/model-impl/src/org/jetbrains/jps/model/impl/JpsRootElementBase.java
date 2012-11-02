package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;

/**
 * @author nik
 */
public abstract class JpsRootElementBase<E extends JpsRootElementBase<E>> extends JpsCompositeElementBase<E> {
  private final JpsModel myModel;
  private final JpsEventDispatcher myEventDispatcher;

  protected JpsRootElementBase(@NotNull JpsModel model, JpsEventDispatcher eventDispatcher) {
    super();
    myModel = model;
    myEventDispatcher = eventDispatcher;
  }

  protected JpsRootElementBase(JpsCompositeElementBase<E> original, JpsModel model, JpsEventDispatcher dispatcher) {
    super(original);
    myModel = model;
    myEventDispatcher = dispatcher;
  }

  @Override
  protected JpsEventDispatcher getEventDispatcher() {
    return myEventDispatcher;
  }

  @NotNull
  @Override
  public JpsModel getModel() {
    return myModel;
  }

  @NotNull
  @Override
  public E createCopy() {
    throw new UnsupportedOperationException("'createCopy' not implemented in " + getClass().getName());
  }
}
