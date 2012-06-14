package org.jetbrains.jps.model.impl;

import org.jetbrains.jps.model.JpsElementType;

/**
 * @author nik
 */
public class JpsTypedDataKind<T extends JpsElementType<?>> extends JpsElementKindBase<JpsTypedDataImpl<T>> {
  public JpsTypedDataKind() {
    super("typed data");
  }
}
