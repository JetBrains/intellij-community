// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations;

import com.intellij.openapi.projectRoots.ProjectJdkTable;
import org.jetbrains.annotations.Nullable;

public interface ConfigurationWithAlternativeJre {
  boolean isAlternativeJrePathEnabled();

  /**
   * @return either JDK name (which allows to get JDK itself via {@link ProjectJdkTable#findJdk(java.lang.String)}),
   * or full path to JRE.
   */
  @Nullable
  String getAlternativeJrePath();
}
