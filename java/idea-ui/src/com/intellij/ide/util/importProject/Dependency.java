// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.importProject;

import org.jetbrains.annotations.ApiStatus;

/**
 * A dependency (library, module, or file)
 */
@ApiStatus.Internal
public sealed interface Dependency permits LibraryDescriptor, FileDescriptor, ModuleDescriptor {
  /**
   * @return dependency weight to sort them. Lower values correspond to smaller dependencies
   */
  int getWeight();
}
