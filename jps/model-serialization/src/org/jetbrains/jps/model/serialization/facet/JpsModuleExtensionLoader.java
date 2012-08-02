package org.jetbrains.jps.model.serialization.facet;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author nik
 */
public abstract class JpsModuleExtensionLoader<E extends JpsElement> {
  private final JpsElementChildRole<E> myRole;
  private final String myFacetTypeId;

  public JpsModuleExtensionLoader(JpsElementChildRole<E> role, String facetTypeId) {
    myRole = role;
    myFacetTypeId = facetTypeId;
  }

  public JpsElementChildRole<E> getRole() {
    return myRole;
  }

  public String getFacetTypeId() {
    return myFacetTypeId;
  }

  public abstract E loadElement(@NotNull Element facetConfigurationElement, String name, String baseModulePath, JpsElement parent);

  public E addElement(JpsModule module, E e) {
    return module.getContainer().setChild(getRole(), e);
  }
}
