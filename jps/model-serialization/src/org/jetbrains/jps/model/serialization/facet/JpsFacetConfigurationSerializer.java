package org.jetbrains.jps.model.serialization.facet;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.List;

/**
 * @author nik
 */
public abstract class JpsFacetConfigurationSerializer<E extends JpsElement> {
  private final JpsElementChildRole<E> myRole;
  private final String myFacetTypeId;
  private final String myFacetName;

  public JpsFacetConfigurationSerializer(JpsElementChildRole<E> role, String facetTypeId, final @Nullable String facetName) {
    myRole = role;
    myFacetTypeId = facetTypeId;
    myFacetName = facetName;
  }

  public String getFacetTypeId() {
    return myFacetTypeId;
  }

  public E loadExtension(final Element configurationElement, final String facetName, JpsModule module, JpsElement parentFacet) {
    final E e = loadExtension(configurationElement, facetName, parentFacet, module);
    return module.getContainer().setChild(myRole, e);
  }

  protected abstract E loadExtension(@NotNull Element facetConfigurationElement, String name, JpsElement parent, JpsModule module);

  public boolean hasExtension(JpsModule module) {
    return module.getContainer().getChild(myRole) != null;
  }

  public void saveExtension(JpsModule module, @NotNull List<FacetState> states) {
    E extension = module.getContainer().getChild(myRole);
    if (extension != null) {
      FacetState state = new FacetState();
      state.setFacetType(myFacetTypeId);
      state.setName(myFacetName);
      Element tag = new Element(JpsFacetSerializer.CONFIGURATION_TAG);
      saveExtension(extension, tag, module);
      state.setConfiguration(tag);
      states.add(state);
    }
  }

  protected abstract void saveExtension(E extension, Element facetConfigurationTag, JpsModule module);
}
