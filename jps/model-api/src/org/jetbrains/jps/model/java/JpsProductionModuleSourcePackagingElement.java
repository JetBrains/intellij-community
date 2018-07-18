// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.module.JpsModuleReference;

public interface JpsProductionModuleSourcePackagingElement extends JpsPackagingElement {
  @NotNull
  JpsModuleReference getModuleReference();
}
