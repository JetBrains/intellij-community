package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.*;

import java.util.List;

/**
 * @author nik
 */
public abstract class JpsNamedElementReferenceBase<S extends JpsNamedElement, T extends JpsNamedElement, Self extends JpsNamedElementReferenceBase<S, T, Self>>
  extends JpsCompositeElementBase<Self> implements JpsElementReference<T> {
  private static final JpsElementChildRole<JpsElementReference<? extends JpsCompositeElement>> PARENT_REFERENCE_ROLE = JpsElementChildRoleBase.create("parent");
  protected final String myElementName;

  protected JpsNamedElementReferenceBase(@NotNull String elementName, @NotNull JpsElementReference<? extends JpsCompositeElement> parentReference) {
    myElementName = elementName;
    myContainer.setChild(PARENT_REFERENCE_ROLE, parentReference);
  }

  protected JpsNamedElementReferenceBase(JpsNamedElementReferenceBase<S, T, Self> original) {
    super(original);
    myElementName = original.myElementName;
  }

  @Override
  public T resolve() {
    final JpsCompositeElement parent = getParentReference().resolve();
    if (parent == null) return null;

    JpsElementCollectionImpl<? extends S> collection = getCollection(parent);
    if (collection == null) return null;

    final List<? extends S> elements = collection.getElements();
    for (S element : elements) {
      if (element.getName().equals(myElementName)) {
        T resolved = resolve(element);
        if (resolved != null) {
          return resolved;
        }
      }
    }
    return null;
  }

  @Nullable
  protected abstract JpsElementCollectionImpl<? extends S> getCollection(@NotNull JpsCompositeElement parent);

  @Nullable
  protected abstract T resolve(S element);

  public JpsElementReference<? extends JpsCompositeElement> getParentReference() {
    return myContainer.getChild(PARENT_REFERENCE_ROLE);
  }

  @Override
  public JpsElementReference<T> asExternal(@NotNull JpsModel model) {
    model.registerExternalReference(this);
    return this;
  }
}
