package org.jetbrains.jps.model.impl;

import org.jetbrains.jps.model.JpsElementProperties;
import org.jetbrains.jps.model.JpsSimpleElement;

/**
 * @author nik
 */
public class SimpleJpsElementRole<P extends JpsElementProperties> extends JpsElementChildRoleBase<JpsSimpleElement<P>> {
  public SimpleJpsElementRole(String debugName) {
    super(debugName);
  }
}
