// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public interface StatusBarInfo {

  /**
   * Set status bar text
   * @param s text to be shown in the status bar
   */
  void setInfo(@NlsContexts.StatusBarText @Nullable String s);

  void setInfo(@NlsContexts.StatusBarText @Nullable String s, @NonNls @Nullable String requestor);

  @NlsContexts.StatusBarText String getInfo();
}
