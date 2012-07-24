package org.jetbrains.jps.model.artifact.impl.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.elements.JpsCompositePackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.impl.JpsCompositeElementBase;
import org.jetbrains.jps.model.impl.JpsElementCollectionKind;
import org.jetbrains.jps.model.impl.JpsElementKindBase;

import java.util.List;

/**
 * @author nik
 */
public abstract class JpsCompositePackagingElementBase<Self extends JpsCompositePackagingElementBase<Self>> extends JpsCompositeElementBase<Self>
  implements JpsCompositePackagingElement {
  private static final JpsElementCollectionKind<JpsPackagingElement> CHILDREN_KIND = new JpsElementCollectionKind<JpsPackagingElement>(new JpsElementKindBase<JpsPackagingElement>("child"));

  protected JpsCompositePackagingElementBase() {
    myContainer.setChild(CHILDREN_KIND);
  }

  protected JpsCompositePackagingElementBase(JpsCompositePackagingElementBase<Self> original) {
    super(original);
  }

  @NotNull
  @Override
  public List<JpsPackagingElement> getChildren() {
    return myContainer.getChild(CHILDREN_KIND).getElements();
  }


  @Override
  public <E extends JpsPackagingElement> E addChild(@NotNull E child) {
    return myContainer.getChild(CHILDREN_KIND).addChild(child);
  }
}
