// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff;

import com.intellij.openapi.application.ApplicationManager;

/**
 * @deprecated use {@link com.intellij.diff.DiffManager} instead
 */
@Deprecated
public abstract class DiffManager {
  public static DiffManager getInstance() {
    return ApplicationManager.getApplication().getService(DiffManager.class);
  }

  public abstract DiffTool getDiffTool();

  /**
   * Use to ignore use settings and get Idea own DiffTool.
   * Internal tool knows hints: <br>
   * {@link DiffTool#HINT_SHOW_MODAL_DIALOG} force show diff in modal dialog <br>
   * {@link DiffTool#HINT_SHOW_FRAME} don't check modal window open.
   * Show diff in frame in any case. May help as workaround when closing
   * modal dialog right before opening diff.<br>
   * {@link DiffTool#HINT_SHOW_NOT_MODAL_DIALOG} Show diff in not modal dialog<br>
   */
  public abstract DiffTool getIdeaDiffTool();
}
