package org.jetbrains.jps.model.impl;

import org.jetbrains.jps.model.JpsElementType;

/**
 * @author nik
 */
public class JpsTypedDataRole<T extends JpsElementType<?>> extends JpsElementChildRoleBase<JpsTypedDataImpl<T>> {
  public JpsTypedDataRole() {
    super("typed data");
  }
}
