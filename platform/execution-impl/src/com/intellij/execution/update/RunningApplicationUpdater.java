// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.update;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Instance of {@link RunningApplicationUpdater} may be provided by {@link RunningApplicationUpdaterProvider}.
 */
public interface RunningApplicationUpdater {
  @NlsActions.ActionText
  String getDescription();

  @Nls
  String getShortName();

  @Nullable
  Icon getIcon();

  /**
   * The method is called on performing update running application action.
   */
  void performUpdate(AnActionEvent event);
}
