package org.jetbrains.jps.model.impl;

import org.jetbrains.jps.model.JpsElementProperties;

/**
 * @author nik
 */
public class SimpleJpsElementKind<P extends JpsElementProperties, Parent extends JpsCompositeElementBase<?>> extends
                                                                                                             JpsElementKindBase<SimpleJpsElementImpl<P>> {
  public SimpleJpsElementKind(String debugName) {
    super(debugName);
  }
}
