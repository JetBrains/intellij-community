package org.jetbrains.jps.model.module;

import org.jetbrains.jps.model.JpsCompositeElement;

/**
 * @author nik
 */
public interface JpsDependencyElement extends JpsCompositeElement {
  void remove();

  JpsModule getContainingModule();
}
