// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.artifact.impl.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.elements.JpsArchivePackagingElement;

class JpsArchivePackagingElementImpl extends JpsCompositePackagingElementBase<JpsArchivePackagingElementImpl>
  implements JpsArchivePackagingElement {
  private String myArchiveName;

  JpsArchivePackagingElementImpl(String archiveName) {
    myArchiveName = archiveName;
  }

  private JpsArchivePackagingElementImpl(JpsArchivePackagingElementImpl original) {
    super(original);
    myArchiveName = original.myArchiveName;
  }

  @Override
  public @NotNull JpsArchivePackagingElementImpl createCopy() {
    return new JpsArchivePackagingElementImpl(this);
  }

  @Override
  public @NotNull JpsArchivePackagingElementImpl createElementCopy() {
    return new JpsArchivePackagingElementImpl(this);
  }

  @Override
  public String getArchiveName() {
    return myArchiveName;
  }

  @Override
  public void setArchiveName(String directoryName) {
    if (!myArchiveName.equals(directoryName)) {
      myArchiveName = directoryName;
    }
  }
}
