// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;

interface CloseableMaplet {
  /**
   */
  void close() throws BuildDataCorruptedException;
}
