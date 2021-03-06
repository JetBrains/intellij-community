// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@ApiStatus.Internal
@ApiStatus.Experimental
public interface IntegrityCheckCapableFileSystem {

  long getEntryCrc(@NotNull VirtualFile file) throws IOException;

}
