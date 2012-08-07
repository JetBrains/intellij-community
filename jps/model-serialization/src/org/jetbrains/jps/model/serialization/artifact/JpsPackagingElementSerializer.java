package org.jetbrains.jps.model.serialization.artifact;

import org.jdom.Element;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;

/**
 * @author nik
 */
public abstract class JpsPackagingElementSerializer<E extends JpsPackagingElement> {
  private final String myTypeId;
  private final Class<? extends E> myElementClass;

  protected JpsPackagingElementSerializer(String typeId, Class<? extends E> elementClass) {
    myTypeId = typeId;
    myElementClass = elementClass;
  }

  public String getTypeId() {
    return myTypeId;
  }

  public Class<? extends E> getElementClass() {
    return myElementClass;
  }

  public abstract E load(Element element);

  public abstract void save(E element, Element tag);
}
