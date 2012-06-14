package org.jetbrains.jps.model.impl;

import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementKind;

/**
 * @author nik
 */
public class JpsElementKindBase<E extends JpsElement> extends JpsElementKind<E> {
  private String myDebugName;

  public JpsElementKindBase(String debugName) {
    myDebugName = debugName;
  }

  @Override
  public String toString() {
    return myDebugName;
  }
}
