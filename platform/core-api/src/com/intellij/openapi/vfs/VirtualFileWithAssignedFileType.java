// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * A way to opt-out from file type detection logic
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public interface VirtualFileWithAssignedFileType {
  @Nullable FileType getAssignedFileType();
}
