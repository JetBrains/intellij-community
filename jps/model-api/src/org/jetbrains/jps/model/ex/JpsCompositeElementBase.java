// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.ex;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElementContainer;

public abstract class JpsCompositeElementBase<Self extends JpsCompositeElementBase<Self>> extends JpsElementBase<Self> implements JpsCompositeElement {
  protected final JpsElementContainerEx myContainer;

  protected JpsCompositeElementBase() {
    myContainer = JpsExElementFactory.getInstance().createContainer(this);
  }

  /**
   * @deprecated creating copies isn't supported in for all elements in JPS anymore; if you need to create a copy for your element,
   * write the corresponding code in your class directly.
   */
  @Deprecated(forRemoval = true)
  protected JpsCompositeElementBase(@NotNull JpsCompositeElementBase<Self> original) {
    myContainer = JpsExElementFactory.getInstance().createContainerCopy(original.myContainer, this);
  }

  @Override
  public @NotNull JpsElementContainer getContainer() {
    return myContainer;
  }
}
