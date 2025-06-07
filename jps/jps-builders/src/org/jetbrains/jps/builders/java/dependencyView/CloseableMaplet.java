// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;

@ApiStatus.Internal
public interface CloseableMaplet {
  /**
   */
  void close() throws BuildDataCorruptedException;
}
