package org.jetbrains.jps.model.ex;

import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsElementContainer;

import java.util.Map;

/**
 * @author nik
 */
public abstract class JpsElementContainerEx implements JpsElementContainer {
  protected abstract Map<JpsElementChildRole<?>, JpsElement> getElementsMap();

  protected abstract void applyChanges(JpsElementContainerEx modified);
}
