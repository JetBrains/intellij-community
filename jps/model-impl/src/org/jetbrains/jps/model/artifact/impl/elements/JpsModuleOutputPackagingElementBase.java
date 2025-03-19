// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.artifact.impl.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.artifact.elements.JpsModuleOutputPackagingElement;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleReference;

public abstract class JpsModuleOutputPackagingElementBase<Self extends JpsModuleOutputPackagingElementBase<Self>> extends JpsCompositeElementBase<Self> implements
                                                                                                                                                        JpsModuleOutputPackagingElement {
  private static final JpsElementChildRole<JpsModuleReference>
    MODULE_REFERENCE_CHILD_ROLE = JpsElementChildRoleBase.create("module reference");

  public JpsModuleOutputPackagingElementBase(JpsModuleReference moduleReference) {
    myContainer.setChild(MODULE_REFERENCE_CHILD_ROLE, moduleReference);
  }

  public JpsModuleOutputPackagingElementBase(JpsModuleOutputPackagingElementBase<Self> original) {
    super(original);
  }

  @Override
  public @NotNull Self createCopy() {
    //noinspection unchecked
    return (Self)createElementCopy();
  }

  @Override
  public @NotNull JpsModuleReference getModuleReference() {
    return myContainer.getChild(MODULE_REFERENCE_CHILD_ROLE);
  }

  @Override
  public @Nullable String getOutputUrl() {
    JpsModule module = getModuleReference().resolve();
    if (module == null) return null;
    return getOutputUrl(module);
  }

  protected abstract @Nullable String getOutputUrl(@NotNull JpsModule module);
}
