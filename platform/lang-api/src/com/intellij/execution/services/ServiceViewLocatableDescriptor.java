// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * {@link ServiceViewDescriptor} may implement this interface in order to enable navigation from sources to a service.
 */
public interface ServiceViewLocatableDescriptor {
  /**
   * Invoked on background thread under read action.
   */
  @Nullable
  default VirtualFile getVirtualFile() {
    return null;
  }
}
