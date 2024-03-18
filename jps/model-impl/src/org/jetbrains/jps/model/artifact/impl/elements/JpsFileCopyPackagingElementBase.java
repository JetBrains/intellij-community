// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.artifact.impl.elements;

import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.ex.JpsElementBase;

import java.util.Objects;

public abstract class JpsFileCopyPackagingElementBase<Self extends JpsFileCopyPackagingElementBase<Self>> extends JpsElementBase<Self> implements
                                                                                                                                 JpsPackagingElement {
  protected String myFilePath;

  public JpsFileCopyPackagingElementBase(String filePath) {
    myFilePath = filePath;
  }

  public String getFilePath() {
    return myFilePath;
  }

  public void setFilePath(String filePath) {
    if (!Objects.equals(myFilePath, filePath)) {
      myFilePath = filePath;
    }
  }
}
