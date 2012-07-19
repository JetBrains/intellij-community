package org.jetbrains.jps.model.serialization.facet;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementKind;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author nik
 */
public abstract class JpsModuleExtensionLoader<E extends JpsElement> {
  private final JpsElementKind<E> myKind;
  private final String myFacetTypeId;

  public JpsModuleExtensionLoader(JpsElementKind<E> kind, String facetTypeId) {
    myKind = kind;
    myFacetTypeId = facetTypeId;
  }

  public JpsElementKind<E> getKind() {
    return myKind;
  }

  public String getFacetTypeId() {
    return myFacetTypeId;
  }

  public abstract E loadElement(@NotNull Element facetConfigurationElement, String name, String baseModulePath, JpsElement parent);

  public E addElement(JpsModule module, E e) {
    return module.getContainer().setChild(getKind(), e);
  }
}
