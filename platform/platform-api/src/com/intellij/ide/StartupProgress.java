package com.intellij.ide;

/**
 * @author Dmitry Avdeev
 *         Date: 7/21/11
 */
public interface StartupProgress {

  /**
   * Displays new progress state.
   * @param message text to be shown
   * @param progress progress state from 0 to 1
   */
  void showProgress(String message, float progress);
}
