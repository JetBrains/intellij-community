package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;

/**
 * @author nik
 */
public class JpsProjectElementReference extends JpsElementReferenceBase<JpsProjectElementReference, JpsProject> {
  @Override
  public JpsProject resolve() {
    final JpsModel model = getModel();
    return model != null ? model.getProject() : null;
  }

  @NotNull
  @Override
  public JpsProjectElementReference createCopy() {
    return new JpsProjectElementReference();
  }

  @Override
  public void applyChanges(@NotNull JpsProjectElementReference modified) {
  }
}
