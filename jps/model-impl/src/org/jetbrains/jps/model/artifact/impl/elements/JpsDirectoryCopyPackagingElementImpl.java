// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.artifact.impl.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.elements.JpsDirectoryCopyPackagingElement;

class JpsDirectoryCopyPackagingElementImpl extends JpsFileCopyPackagingElementBase<JpsDirectoryCopyPackagingElementImpl>
  implements JpsDirectoryCopyPackagingElement {
  JpsDirectoryCopyPackagingElementImpl(String directoryPath) {
    super(directoryPath);
  }

  @Override
  public @NotNull JpsDirectoryCopyPackagingElementImpl createElementCopy() {
    return new JpsDirectoryCopyPackagingElementImpl(myFilePath);
  }

  @Override
  public String getDirectoryPath() {
    return getFilePath();
  }

  @Override
  public void setDirectoryPath(String path) {
    setFilePath(path);
  }
}
