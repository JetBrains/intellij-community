// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

public interface ConfigurationCreationListener {
  /**
   * Called when configuration created via UI (Add Configuration).
   * Suitable to perform some initialization tasks (in most cases it is indicator that you do something wrong, so, please override this method with care and only if really need).
   */
  void onNewConfigurationCreated();

  void onConfigurationCopied();
}
