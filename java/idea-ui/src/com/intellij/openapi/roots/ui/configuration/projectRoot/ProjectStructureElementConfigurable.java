// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.ui.NamedConfigurable;
import org.jetbrains.annotations.Nullable;

public abstract class ProjectStructureElementConfigurable<T> extends NamedConfigurable<T> {
  protected ProjectStructureElementConfigurable() {
  }

  protected ProjectStructureElementConfigurable(boolean isNameEditable, @Nullable Runnable updateTree) {
    super(isNameEditable, updateTree);
  }

  public abstract @Nullable ProjectStructureElement getProjectStructureElement();
}
