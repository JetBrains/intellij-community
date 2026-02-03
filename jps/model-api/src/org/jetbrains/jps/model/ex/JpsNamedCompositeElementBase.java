// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.ex;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsNamedElement;

public abstract class JpsNamedCompositeElementBase<Self extends JpsNamedCompositeElementBase<Self>> extends JpsCompositeElementBase<Self>
  implements JpsNamedElement {
  private @NlsSafe String name;

  protected JpsNamedCompositeElementBase(@NlsSafe @NotNull String name) {
    super();

    this.name = name;
  }

  /**
   * @deprecated creating copies isn't supported in for all elements in JPS anymore; if you need to create a copy for your element,
   * write the corresponding code in your class directly.
   */
  @Deprecated
  protected JpsNamedCompositeElementBase(@NotNull JpsNamedCompositeElementBase<Self> original) {
    super(original);

    name = original.name;
  }

  @Override
  public @NotNull String getName() {
    return name;
  }

  @Override
  public void setName(@NlsSafe @NotNull String name) {
    this.name = name;
  }
}
