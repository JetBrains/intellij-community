// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/**
 * An interface to be (optionally, but preferably) implemented by {@link QueryExecutor} parameters to provide additional data.
 */
public interface QueryParameters {
  default @Nullable Project getProject() {
    return null;
  }

  /**
   * @return whether this query still makes sense (e.g. PSI inside is still valid)
   */
  default boolean isQueryValid() {
    return true;
  }
}
