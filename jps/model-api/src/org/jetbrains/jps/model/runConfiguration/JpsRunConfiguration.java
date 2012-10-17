package org.jetbrains.jps.model.runConfiguration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsNamedElement;

/**
 * @author nik
 */
public interface JpsRunConfiguration extends JpsNamedElement, JpsCompositeElement {
  @NotNull
  JpsElement getProperties();
}
