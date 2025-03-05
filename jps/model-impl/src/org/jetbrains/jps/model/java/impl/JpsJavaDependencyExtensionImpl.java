// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.java.JpsJavaDependencyExtension;
import org.jetbrains.jps.model.java.JpsJavaDependencyScope;

class JpsJavaDependencyExtensionImpl extends JpsElementBase<JpsJavaDependencyExtensionImpl> implements JpsJavaDependencyExtension {
  private boolean myExported;
  private JpsJavaDependencyScope myScope;

  JpsJavaDependencyExtensionImpl(boolean exported,
                                        JpsJavaDependencyScope scope) {
    myExported = exported;
    myScope = scope;
  }

  JpsJavaDependencyExtensionImpl(JpsJavaDependencyExtensionImpl original) {
    myExported = original.myExported;
    myScope = original.myScope;
  }

  @Override
  public boolean isExported() {
    return myExported;
  }

  @Override
  public void setExported(boolean exported) {
    if (myExported != exported) {
      myExported = exported;
    }
  }

  @Override
  public @NotNull JpsJavaDependencyScope getScope() {
    return myScope;
  }

  @Override
  public void setScope(@NotNull JpsJavaDependencyScope scope) {
    if (!scope.equals(myScope)) {
      myScope = scope;
    }
  }

  @Override
  public @NotNull JpsJavaDependencyExtensionImpl createCopy() {
    return new JpsJavaDependencyExtensionImpl(this);
  }
}
