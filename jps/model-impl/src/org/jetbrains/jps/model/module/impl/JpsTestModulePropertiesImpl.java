// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.module.impl;

import com.intellij.openapi.util.NullableLazyValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleReference;
import org.jetbrains.jps.model.module.JpsTestModuleProperties;

import static com.intellij.openapi.util.NullableLazyValue.volatileLazyNullable;

public class JpsTestModulePropertiesImpl extends JpsCompositeElementBase<JpsTestModulePropertiesImpl> implements JpsTestModuleProperties {
  public static final JpsElementChildRole<JpsTestModuleProperties> ROLE = JpsElementChildRoleBase.create("test module properties");

  private static final JpsElementChildRole<JpsModuleReference> MODULE_REFERENCE_CHILD_ROLE = JpsElementChildRoleBase.create("production module reference");

  private final NullableLazyValue<JpsModule> myCachedProductionModule = volatileLazyNullable(() -> getProductionModuleReference().resolve());

  public JpsTestModulePropertiesImpl(@NotNull JpsModuleReference productionModuleReference) {
    myContainer.setChild(MODULE_REFERENCE_CHILD_ROLE, productionModuleReference);
  }

  private JpsTestModulePropertiesImpl(JpsTestModulePropertiesImpl original) {
    super(original);
  }

  @NotNull
  @Override
  public JpsModuleReference getProductionModuleReference() {
    return myContainer.getChild(MODULE_REFERENCE_CHILD_ROLE);
  }

  @Nullable
  @Override
  public JpsModule getProductionModule() {
    return myCachedProductionModule.getValue();
  }


  @NotNull
  @Override
  public JpsTestModulePropertiesImpl createCopy() {
    return new JpsTestModulePropertiesImpl(this);
  }
}
