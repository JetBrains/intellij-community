package org.jetbrains.jps.model.impl;

import org.jetbrains.jps.model.JpsElementProperties;
import org.jetbrains.jps.model.SimpleJpsElement;

/**
 * @author nik
 */
public class SimpleJpsElementKind<P extends JpsElementProperties> extends JpsElementKindBase<SimpleJpsElement<P>> {
  public SimpleJpsElementKind(String debugName) {
    super(debugName);
  }
}
