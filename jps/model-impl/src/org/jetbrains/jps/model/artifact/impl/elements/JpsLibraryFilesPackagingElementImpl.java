// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.artifact.impl.elements;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.artifact.elements.JpsLibraryFilesPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElementFactory;
import org.jetbrains.jps.model.artifact.elements.ex.JpsComplexPackagingElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.library.JpsOrderRootType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class JpsLibraryFilesPackagingElementImpl extends JpsComplexPackagingElementBase<JpsLibraryFilesPackagingElementImpl> implements JpsLibraryFilesPackagingElement {
  private static final JpsElementChildRole<JpsLibraryReference>
    LIBRARY_REFERENCE_CHILD_ROLE = JpsElementChildRoleBase.create("library reference");

  JpsLibraryFilesPackagingElementImpl(@NotNull JpsLibraryReference reference) {
    myContainer.setChild(LIBRARY_REFERENCE_CHILD_ROLE, reference);
  }

  private JpsLibraryFilesPackagingElementImpl(JpsLibraryFilesPackagingElementImpl original) {
    super(original);
  }

  @Override
  public @NotNull JpsLibraryFilesPackagingElementImpl createElementCopy() {
    return new JpsLibraryFilesPackagingElementImpl(this);
  }

  @Override
  public @NotNull JpsLibraryReference getLibraryReference() {
    return myContainer.getChild(LIBRARY_REFERENCE_CHILD_ROLE);
  }

  @Override
  public List<JpsPackagingElement> getSubstitution() {
    JpsLibrary library = getLibraryReference().resolve();
    if (library == null) return Collections.emptyList();
    List<JpsPackagingElement> result = new ArrayList<>();
    for (File file : library.getFiles(JpsOrderRootType.COMPILED)) {
      String path = FileUtil.toSystemIndependentName(file.getAbsolutePath());
      if (file.isDirectory()) {
        result.add(JpsPackagingElementFactory.getInstance().createDirectoryCopy(path));
      }
      else {
        result.add(JpsPackagingElementFactory.getInstance().createFileCopy(path, null));
      }
    }
    return result;
  }
}
