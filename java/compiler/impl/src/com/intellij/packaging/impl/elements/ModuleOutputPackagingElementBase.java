// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.elements;

import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.elements.PackagingElementType;

public abstract class ModuleOutputPackagingElementBase extends ModulePackagingElementBase implements ModuleOutputPackagingElement {
  public ModuleOutputPackagingElementBase(PackagingElementType type,
                                          Project project,
                                          ModulePointer modulePointer) {
    super(type, project, modulePointer);
  }

  public ModuleOutputPackagingElementBase(PackagingElementType type, Project project) {
    super(type, project);
  }
}
