// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * The listener interface to receive code style changes events. Code style settings may change due to an explicit settings change by
 * end user or a programmatic change which may also be a result of asynchronous background computation. Any class using current code
 * style settings must listen to the change events in order to use up-to-date settings.
 *
 * @see CodeStyleSettingsManager#notifyCodeStyleSettingsChanged()
 * @see com.intellij.application.options.CodeStyle#getSettings(PsiFile)
 */
public interface CodeStyleSettingsListener {
  /**
   * Invoked when the code style settings change.
   *
   * @param event The code style change event.
   */
  void codeStyleSettingsChanged(@NotNull CodeStyleSettingsChangeEvent event);
}
