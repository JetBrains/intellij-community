// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.impl.elements.JpsModuleOutputPackagingElementBase;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsProductionModuleOutputPackagingElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleReference;

class JpsProductionModuleOutputPackagingElementImpl extends JpsModuleOutputPackagingElementBase<JpsProductionModuleOutputPackagingElementImpl>
  implements JpsProductionModuleOutputPackagingElement {
  JpsProductionModuleOutputPackagingElementImpl(JpsModuleReference moduleReference) {
    super(moduleReference);
  }

  private JpsProductionModuleOutputPackagingElementImpl(JpsProductionModuleOutputPackagingElementImpl original) {
    super(original);
  }

  @Override
  public @NotNull JpsProductionModuleOutputPackagingElementImpl createElementCopy() {
    return new JpsProductionModuleOutputPackagingElementImpl(this);
  }

  @Override
  protected String getOutputUrl(@NotNull JpsModule module) {
    return JpsJavaExtensionService.getInstance().getOutputUrl(module, false);
  }
}
