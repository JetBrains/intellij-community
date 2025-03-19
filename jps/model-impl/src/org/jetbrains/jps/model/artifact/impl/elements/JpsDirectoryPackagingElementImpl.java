// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.artifact.impl.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.elements.JpsDirectoryPackagingElement;

class JpsDirectoryPackagingElementImpl extends JpsCompositePackagingElementBase<JpsDirectoryPackagingElementImpl> implements JpsDirectoryPackagingElement {
  private String myDirectoryName;

  JpsDirectoryPackagingElementImpl(String directoryName) {
    myDirectoryName = directoryName;
  }

  private JpsDirectoryPackagingElementImpl(JpsDirectoryPackagingElementImpl original) {
    super(original);
    myDirectoryName = original.myDirectoryName;
  }

  @Override
  public @NotNull JpsDirectoryPackagingElementImpl createCopy() {
    return new JpsDirectoryPackagingElementImpl(this);
  }

  @Override
  public @NotNull JpsDirectoryPackagingElementImpl createElementCopy() {
    return new JpsDirectoryPackagingElementImpl(this);
  }

  @Override
  public String getDirectoryName() {
    return myDirectoryName;
  }

  @Override
  public void setDirectoryName(String directoryName) {
    if (!myDirectoryName.equals(directoryName)) {
      myDirectoryName = directoryName;
    }
  }
}
