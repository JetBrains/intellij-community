package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;

/**
 * @author nik
 */
public class JpsProjectElementReference extends JpsElementBase<JpsProjectElementReference> implements JpsElementReference<JpsProject> {
  private final JpsModel myModel;

  public JpsProjectElementReference(JpsModel model, JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    super(eventDispatcher, parent);
    myModel = model;
  }

  @Override
  public JpsProject resolve() {
    return myModel.getProject();
  }

  @NotNull
  @Override
  public JpsProjectElementReference createCopy(@NotNull JpsModel model,
                                               @NotNull JpsEventDispatcher eventDispatcher,
                                               JpsParentElement parent) {
    return new JpsProjectElementReference(model, eventDispatcher, parent);
  }

  @Override
  public void applyChanges(@NotNull JpsProjectElementReference modified) {
  }
}
