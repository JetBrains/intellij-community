// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

/**
 * @author Dmitry Avdeev
 */
public interface StartupProgress {
  /**
   * Displays new progress state.
   * @param progress progress state from 0 to 1
   */
  void showProgress(double progress);
}
