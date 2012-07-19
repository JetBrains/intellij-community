package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementCollection;
import org.jetbrains.jps.model.JpsElementKind;
import org.jetbrains.jps.model.JpsElementProperties;
import org.jetbrains.jps.model.impl.JpsElementKindBase;
import org.jetbrains.jps.model.impl.JpsNamedCompositeElementBase;
import org.jetbrains.jps.model.impl.JpsTypedDataImpl;
import org.jetbrains.jps.model.impl.JpsTypedDataKind;
import org.jetbrains.jps.model.module.JpsFacet;
import org.jetbrains.jps.model.module.JpsFacetReference;
import org.jetbrains.jps.model.module.JpsFacetType;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author nik
 */
public class JpsFacetImpl extends JpsNamedCompositeElementBase<JpsFacetImpl> implements JpsFacet {
  private static final JpsTypedDataKind<JpsFacetType<?>> TYPED_DATA_KIND = new JpsTypedDataKind<JpsFacetType<?>>();
  private static final JpsElementKind<JpsFacetReference> PARENT_FACET_REFERENCE = new JpsElementKindBase<JpsFacetReference>("parent facet");

  public <P extends JpsElementProperties>JpsFacetImpl(JpsFacetType<?> facetType, @NotNull String name, @NotNull P properties) {
    super(name);
    myContainer.setChild(TYPED_DATA_KIND, new JpsTypedDataImpl<JpsFacetType<?>>(facetType, properties));
    myContainer.setChild(JpsFacetKind.COLLECTION_KIND);
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

  public <P extends JpsElementProperties> P getProperties(@NotNull JpsFacetType<P> type) {
    return myContainer.getChild(TYPED_DATA_KIND).getProperties(type);
  }

  @Override
  public void setParentFacet(@NotNull JpsFacet facet) {
    myContainer.setChild(PARENT_FACET_REFERENCE, facet.createReference());
  }

  @Override
  @Nullable
  public JpsFacet getParentFacet() {
    final JpsFacetReference reference = myContainer.getChild(PARENT_FACET_REFERENCE);
    return reference != null ? reference.resolve() : null;
  }

  @Override
  public JpsModule getModule() {
    return myParent != null ? (JpsModule)myParent.getParent() : null;
  }

  @NotNull
  @Override
  public JpsFacetReference createReference() {
    return new JpsFacetReferenceImpl(getName(), getModule().createReference());
  }

  @Override
  public void delete() {
    //noinspection unchecked
    ((JpsElementCollection<JpsFacet>)myParent).removeChild(this);
  }
}
