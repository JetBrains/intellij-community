// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.relativizer;

import org.jetbrains.annotations.Nullable;

class BuildDataPathRelativizer extends CommonPathRelativizer{
  private static final String IDENTIFIER = "$BUILD_DIR$";

  BuildDataPathRelativizer(@Nullable String buildDir) {
    super(buildDir, IDENTIFIER);
  }
}
