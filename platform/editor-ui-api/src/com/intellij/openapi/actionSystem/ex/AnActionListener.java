// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;

/**
 * @author Kirill Kalishev
 * @author Konstantin Bulenkov
 */
public interface AnActionListener {
  default void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
  }

  /**
   * Note that using {@code dataContext} in implementing methods is unsafe - it could have been invalidated by the performed action.
   */
  default void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
  }

  default void beforeEditorTyping(char c, DataContext dataContext) {
  }

  abstract class Adapter implements AnActionListener {
  }
}
