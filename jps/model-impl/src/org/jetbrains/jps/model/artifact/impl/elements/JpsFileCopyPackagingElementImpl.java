// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.artifact.impl.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.elements.JpsFileCopyPackagingElement;

import java.util.Objects;

class JpsFileCopyPackagingElementImpl extends JpsFileCopyPackagingElementBase<JpsFileCopyPackagingElementImpl> implements JpsFileCopyPackagingElement {
  private String myRenamedOutputFileName;

  JpsFileCopyPackagingElementImpl(String filePath, String renamedOutputFileName) {
    super(filePath);
    myRenamedOutputFileName = renamedOutputFileName;
  }

  @Override
  public @NotNull JpsFileCopyPackagingElementImpl createElementCopy() {
    return new JpsFileCopyPackagingElementImpl(myFilePath, myRenamedOutputFileName);
  }

  @Override
  public String getRenamedOutputFileName() {
    return myRenamedOutputFileName;
  }

  @Override
  public void setRenamedOutputFileName(String renamedOutputFileName) {
    if (!Objects.equals(myRenamedOutputFileName, renamedOutputFileName)) {
      myRenamedOutputFileName = renamedOutputFileName;
    }
  }
}
