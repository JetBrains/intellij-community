// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.elements;

import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.elements.PackagingElementType;
import org.jetbrains.annotations.NotNull;

public abstract class ModuleOutputPackagingElementBase extends ModulePackagingElementBase implements ModuleOutputPackagingElement {
  public ModuleOutputPackagingElementBase(PackagingElementType type,
                                          Project project,
                                          ModulePointer modulePointer) {
    super(type, project, modulePointer);
  }

  @Override
  public @NotNull PackagingElementOutputKind getFilesKind(PackagingElementResolvingContext context) {
    return PackagingElementOutputKind.DIRECTORIES_WITH_CLASSES;
  }

  public ModuleOutputPackagingElementBase(PackagingElementType type, Project project) {
    super(type, project);
  }
}
