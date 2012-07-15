package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsNamedElement;
import org.jetbrains.jps.model.JpsReferenceableElement;

/**
 * @author nik
 */
//todo[nik] I'm not sure that we really need separate interface for facets in the project model.
//Perhaps facets should be replaced by extensions for module elements
public interface JpsFacet extends JpsNamedElement, JpsReferenceableElement<JpsFacet> {

  JpsModule getModule();

  @NotNull
  JpsFacetType<?> getType();

  void delete();

  @NotNull
  @Override
  JpsFacetReference createReference();

  void setParentFacet(@NotNull JpsFacet facet);

  @Nullable
  JpsFacet getParentFacet();
}
