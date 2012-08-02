package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;

import java.util.List;

/**
 * @author nik
 */
public abstract class JpsNamedElementReferenceBase<T extends JpsNamedElement, Self extends JpsNamedElementReferenceBase<T, Self>>
  extends JpsCompositeElementBase<Self> implements JpsElementReference<T> {
  private static final JpsElementChildRole<JpsElementReference<? extends JpsCompositeElement>> PARENT_REFERENCE_ROLE = JpsElementChildRoleBase.create("parent");
  private final JpsElementCollectionRole<? extends T> myCollectionRole;
  protected final String myElementName;

  protected JpsNamedElementReferenceBase(@NotNull JpsElementCollectionRole<? extends T> role,
                                         @NotNull String elementName,
                                         @NotNull JpsElementReference<? extends JpsCompositeElement> parentReference) {
    super();
    myCollectionRole = role;
    myElementName = elementName;
    myContainer.setChild(PARENT_REFERENCE_ROLE, parentReference);
  }

  protected JpsNamedElementReferenceBase(JpsNamedElementReferenceBase<T, Self> original) {
    super(original);
    myCollectionRole = original.myCollectionRole;
    myElementName = original.myElementName;
  }

  @Override
  public T resolve() {
    final JpsCompositeElement parent = getParentReference().resolve();
    if (parent == null) return null;

    final List<? extends T> elements = parent.getContainer().getChild(myCollectionRole).getElements();
    for (T element : elements) {
      if (resolvesTo(element)) {
        return element;
      }
    }
    return null;
  }

  protected boolean resolvesTo(T element) {
    return element.getName().equals(myElementName);
  }

  public JpsElementReference<? extends JpsCompositeElement> getParentReference() {
    return myContainer.getChild(PARENT_REFERENCE_ROLE);
  }
}
