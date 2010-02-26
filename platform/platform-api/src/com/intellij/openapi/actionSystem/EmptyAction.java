/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Gregory.Shrago
 */
public class EmptyAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
  }

  public static void setupAction(@NotNull AnAction action, @NotNull String id, @Nullable JComponent component) {
    final AnAction emptyAction = ActionManager.getInstance().getAction(id);
    if (action.getTemplatePresentation().getIcon() == null) {
      action.getTemplatePresentation().setIcon(emptyAction.getTemplatePresentation().getIcon());
    }
    action.getTemplatePresentation().setText(emptyAction.getTemplatePresentation().getText());
    action.registerCustomShortcutSet(emptyAction.getShortcutSet(), component);
  }
}
