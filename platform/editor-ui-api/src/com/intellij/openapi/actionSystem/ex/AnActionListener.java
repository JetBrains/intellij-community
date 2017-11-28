/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;

/**
 * @author Kirill Kalishev
 * @author Konstantin Bulenkov
 */
public interface AnActionListener {
  void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event);

  /**
   * Note that using {@code dataContext} in implementing methods is unsafe - it could have been invalidated by the performed action.
   */
  default void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
  }

  default void beforeEditorTyping(char c, DataContext dataContext) {
  }

  abstract class Adapter implements AnActionListener {
    @Override
    public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {}

    @Override
    public void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {}

    @Override
    public void beforeEditorTyping(char c, DataContext dataContext) {}
  }
}
