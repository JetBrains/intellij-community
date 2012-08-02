package org.jetbrains.jps.model.impl;

import org.jetbrains.jps.model.JpsElementProperties;
import org.jetbrains.jps.model.SimpleJpsElement;

/**
 * @author nik
 */
public class SimpleJpsElementRole<P extends JpsElementProperties> extends JpsElementChildRoleBase<SimpleJpsElement<P>> {
  public SimpleJpsElementRole(String debugName) {
    super(debugName);
  }
}
