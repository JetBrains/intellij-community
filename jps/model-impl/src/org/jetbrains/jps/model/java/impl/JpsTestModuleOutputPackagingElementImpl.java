// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.impl.elements.JpsModuleOutputPackagingElementBase;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsTestModuleOutputPackagingElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleReference;

final class JpsTestModuleOutputPackagingElementImpl extends JpsModuleOutputPackagingElementBase<JpsTestModuleOutputPackagingElementImpl>
  implements JpsTestModuleOutputPackagingElement {
  JpsTestModuleOutputPackagingElementImpl(JpsModuleReference moduleReference) {
    super(moduleReference);
  }

  private JpsTestModuleOutputPackagingElementImpl(JpsTestModuleOutputPackagingElementImpl original) {
    super(original);
  }

  @Override
  public @NotNull JpsTestModuleOutputPackagingElementImpl createElementCopy() {
    return new JpsTestModuleOutputPackagingElementImpl(this);
  }

  @Override
  protected String getOutputUrl(@NotNull JpsModule module) {
    return JpsJavaExtensionService.getInstance().getOutputUrl(module, true);
  }
}
