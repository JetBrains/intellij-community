package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementCollection;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.impl.JpsNamedCompositeElementBase;
import org.jetbrains.jps.model.impl.JpsTypedDataImpl;
import org.jetbrains.jps.model.impl.JpsTypedDataKind;
import org.jetbrains.jps.model.module.JpsFacet;
import org.jetbrains.jps.model.module.JpsFacetType;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author nik
 */
public class JpsFacetImpl extends JpsNamedCompositeElementBase<JpsFacetImpl> implements JpsFacet {
  private static final JpsTypedDataKind<JpsFacetType<?>> TYPED_DATA_KIND = new JpsTypedDataKind<JpsFacetType<?>>();

  public JpsFacetImpl(JpsFacetType<?> facetType, @NotNull String name) {
    super(name);
    myContainer.setChild(TYPED_DATA_KIND, new JpsTypedDataImpl<JpsFacetType<?>>(facetType, facetType.createDefaultProperties()));
  }

  private JpsFacetImpl(JpsNamedCompositeElementBase<JpsFacetImpl> original) {
    super(original);
  }

  @NotNull
  @Override
  public JpsFacetImpl createCopy() {
    return new JpsFacetImpl(this);
  }

  @Override
  @NotNull
  public JpsFacetType<?> getType() {
    return myContainer.getChild(TYPED_DATA_KIND).getType();
  }

  @Override
  public JpsModule getModule() {
    return myParent != null ? (JpsModule)myParent.getParent() : null;
  }

  @NotNull
  @Override
  public JpsElementReference<JpsFacet> createReference() {
    return new JpsFacetReferenceImpl(getName(), getModule().createReference());
  }

  @Override
  public void delete() {
    //noinspection unchecked
    ((JpsElementCollection<JpsFacet>)myParent).removeChild(this);
  }
}
