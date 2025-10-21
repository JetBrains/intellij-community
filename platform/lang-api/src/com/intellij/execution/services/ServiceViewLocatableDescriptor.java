// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated This interface is deprecated and marked for removal.
 * For locating services in the service view, refer to {@link com.intellij.platform.execution.serviceView.backend.ServiceViewLocatableSearcher}.
 */
@Deprecated(forRemoval = true)
public interface ServiceViewLocatableDescriptor {
  default @Nullable VirtualFile getVirtualFile() {
    return null;
  }
}
