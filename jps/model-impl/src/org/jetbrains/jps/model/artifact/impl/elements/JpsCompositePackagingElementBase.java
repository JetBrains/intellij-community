package org.jetbrains.jps.model.artifact.impl.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.elements.JpsCompositePackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.impl.JpsCompositeElementBase;
import org.jetbrains.jps.model.impl.JpsElementChildRoleBase;
import org.jetbrains.jps.model.impl.JpsElementCollectionRole;

import java.util.List;

/**
 * @author nik
 */
public abstract class JpsCompositePackagingElementBase<Self extends JpsCompositePackagingElementBase<Self>> extends JpsCompositeElementBase<Self>
  implements JpsCompositePackagingElement {
  private static final JpsElementCollectionRole<JpsPackagingElement> CHILDREN_ROLE = JpsElementCollectionRole.create(JpsElementChildRoleBase.<JpsPackagingElement>create("child"));

  protected JpsCompositePackagingElementBase() {
    myContainer.setChild(CHILDREN_ROLE);
  }

  protected JpsCompositePackagingElementBase(JpsCompositePackagingElementBase<Self> original) {
    super(original);
  }

  @NotNull
  @Override
  public List<JpsPackagingElement> getChildren() {
    return myContainer.getChild(CHILDREN_ROLE).getElements();
  }


  @Override
  public <E extends JpsPackagingElement> E addChild(@NotNull E child) {
    return myContainer.getChild(CHILDREN_ROLE).addChild(child);
  }
}
