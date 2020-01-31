// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface JdkFinder {
  @NotNull
  static JdkFinder getInstance() {
    return ApplicationManager.getApplication().getService(JdkFinder.class);
  }

  /**
   * Tries to find existing Java SDKs on this computer.
   * If no JDK found, returns possible folders to start file chooser.
   * @return suggested sdk home paths (sorted)
   */
  @NotNull
  List<String> suggestHomePaths();
}
