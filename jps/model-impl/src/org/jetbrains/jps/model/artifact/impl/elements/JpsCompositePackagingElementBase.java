// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.artifact.impl.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.elements.JpsCompositePackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.ex.JpsElementCollectionRole;

import java.util.List;

public abstract class JpsCompositePackagingElementBase<Self extends JpsCompositePackagingElementBase<Self>> extends JpsCompositeElementBase<Self>
  implements JpsCompositePackagingElement {
  private static final JpsElementCollectionRole<JpsPackagingElement> CHILDREN_ROLE = JpsElementCollectionRole.create(JpsElementChildRoleBase.create("child"));

  protected JpsCompositePackagingElementBase() {
    myContainer.setChild(CHILDREN_ROLE);
  }

  protected JpsCompositePackagingElementBase(JpsCompositePackagingElementBase<Self> original) {
    super(original);
  }

  @Override
  public @NotNull List<JpsPackagingElement> getChildren() {
    return myContainer.getChild(CHILDREN_ROLE).getElements();
  }


  @Override
  public <E extends JpsPackagingElement> E addChild(@NotNull E child) {
    return myContainer.getChild(CHILDREN_ROLE).addChild(child);
  }

  @Override
  public void removeChild(@NotNull JpsPackagingElement child) {
    myContainer.getChild(CHILDREN_ROLE).removeChild(child);
  }
}
