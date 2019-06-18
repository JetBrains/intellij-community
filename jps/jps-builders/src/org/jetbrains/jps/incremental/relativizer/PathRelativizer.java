// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.relativizer;

import org.jetbrains.annotations.NotNull;

public interface PathRelativizer {
  boolean isAcceptableAbsolutePath(@NotNull String path);
  boolean isAcceptableRelativePath(@NotNull String path);
  String toRelativePath(@NotNull String path);
  String toAbsolutePath(@NotNull String path);
}
