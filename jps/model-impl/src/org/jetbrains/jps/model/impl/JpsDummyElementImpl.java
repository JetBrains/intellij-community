package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.ex.JpsElementBase;

/**
 * @author nik
 */
public class JpsDummyElementImpl extends JpsElementBase<JpsDummyElementImpl> implements JpsDummyElement {
  @NotNull
  @Override
  public JpsDummyElementImpl createCopy() {
    return new JpsDummyElementImpl();
  }

  @Override
  public void applyChanges(@NotNull JpsDummyElementImpl modified) {
  }
}
