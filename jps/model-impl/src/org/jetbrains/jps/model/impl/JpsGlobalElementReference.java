package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;

/**
 * @author nik
 */
public class JpsGlobalElementReference extends JpsElementBase<JpsGlobalElementReference> implements JpsElementReference<JpsGlobal> {
  private final JpsModel myModel;

  public JpsGlobalElementReference(JpsModel model, JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    super(eventDispatcher, parent);
    myModel = model;
  }

  @Override
  public JpsGlobal resolve() {
    return myModel.getGlobal();
  }

  @NotNull
  @Override
  public JpsGlobalElementReference createCopy(@NotNull JpsModel model,
                                              @NotNull JpsEventDispatcher eventDispatcher,
                                              JpsParentElement parent) {
    return new JpsGlobalElementReference(model, eventDispatcher, parent);
  }

  @Override
  public void applyChanges(@NotNull JpsGlobalElementReference modified) {
  }
}
