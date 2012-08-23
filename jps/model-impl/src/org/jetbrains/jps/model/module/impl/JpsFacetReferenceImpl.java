package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.impl.JpsNamedElementReferenceImpl;
import org.jetbrains.jps.model.module.JpsFacet;
import org.jetbrains.jps.model.module.JpsFacetReference;
import org.jetbrains.jps.model.module.JpsModuleReference;

/**
 * @author nik
 */
public class JpsFacetReferenceImpl extends JpsNamedElementReferenceImpl<JpsFacet, JpsFacetReferenceImpl> implements JpsFacetReference {
  public JpsFacetReferenceImpl(String facetName, JpsModuleReference moduleReference) {
    super(JpsFacetRole.COLLECTION_ROLE, facetName, moduleReference);
  }

  private JpsFacetReferenceImpl(JpsFacetReferenceImpl original) {
    super(original);
  }

  @NotNull
  @Override
  public JpsFacetReferenceImpl createCopy() {
    return new JpsFacetReferenceImpl(this);
  }
}
