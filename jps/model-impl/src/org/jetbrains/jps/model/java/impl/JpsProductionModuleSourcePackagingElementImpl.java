// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.java.JpsProductionModuleSourcePackagingElement;
import org.jetbrains.jps.model.module.JpsModuleReference;

final class JpsProductionModuleSourcePackagingElementImpl extends JpsCompositeElementBase<JpsProductionModuleSourcePackagingElementImpl>
  implements JpsProductionModuleSourcePackagingElement {

  private static final JpsElementChildRole<JpsModuleReference>
    MODULE_REFERENCE_CHILD_ROLE = JpsElementChildRoleBase.create("module reference");

  JpsProductionModuleSourcePackagingElementImpl(JpsModuleReference moduleReference) {
    myContainer.setChild(MODULE_REFERENCE_CHILD_ROLE, moduleReference);
  }

  @Override
  public @NotNull JpsModuleReference getModuleReference() {
    return myContainer.getChild(MODULE_REFERENCE_CHILD_ROLE);
  }

  private JpsProductionModuleSourcePackagingElementImpl(JpsProductionModuleSourcePackagingElementImpl original) {
    super(original);
  }

  @Override
  public @NotNull JpsProductionModuleSourcePackagingElementImpl createCopy() {
    return new JpsProductionModuleSourcePackagingElementImpl(this);
  }

  @Override
  public @NotNull JpsProductionModuleSourcePackagingElementImpl createElementCopy() {
    return new JpsProductionModuleSourcePackagingElementImpl(this);
  }

}
