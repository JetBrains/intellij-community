// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots;

import org.jetbrains.annotations.Nullable;

public interface DefaultJdkConfigurator {
  /**
   * Guess the best JDK location on this system
   * 
   * @return absolute path to JDK home, or null if nothing is found
   */
  @Nullable String guessJavaHome();
}
