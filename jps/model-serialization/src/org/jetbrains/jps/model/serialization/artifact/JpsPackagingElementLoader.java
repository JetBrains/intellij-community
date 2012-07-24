package org.jetbrains.jps.model.serialization.artifact;

import org.jdom.Element;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;

/**
 * @author nik
 */
public abstract class JpsPackagingElementLoader<E extends JpsPackagingElement> {
  private final String myTypeId;

  protected JpsPackagingElementLoader(String typeId) {
    myTypeId = typeId;
  }

  public String getTypeId() {
    return myTypeId;
  }

  public abstract E load(Element element);
}
