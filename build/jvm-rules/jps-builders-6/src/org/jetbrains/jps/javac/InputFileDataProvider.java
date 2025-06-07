// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.tools.*;
import java.util.Set;

@ApiStatus.Internal
public interface InputFileDataProvider {
  
  interface FileData {
    @NotNull
    String getPath();

    @NotNull
    byte[] getContent();
  }

  @Nullable
  Iterable<FileData> list(JavaFileManager.Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse);
}
