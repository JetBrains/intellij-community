// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.artifact.impl.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.elements.JpsExtractedDirectoryPackagingElement;

import java.util.Objects;

class JpsExtractedDirectoryPackagingElementImpl extends JpsFileCopyPackagingElementBase<JpsExtractedDirectoryPackagingElementImpl>
  implements JpsExtractedDirectoryPackagingElement {
  private String myPathInJar;

  JpsExtractedDirectoryPackagingElementImpl(String filePath, String pathInJar) {
    super(filePath);
    myPathInJar = pathInJar;
  }

  @Override
  public @NotNull JpsExtractedDirectoryPackagingElementImpl createElementCopy() {
    return new JpsExtractedDirectoryPackagingElementImpl(myFilePath, myPathInJar);
  }

  @Override
  public String getPathInJar() {
    return myPathInJar;
  }

  @Override
  public void setPathInJar(String pathInJar) {
    if (!Objects.equals(myPathInJar, pathInJar)) {
      myPathInJar = pathInJar;
    }
  }
}
