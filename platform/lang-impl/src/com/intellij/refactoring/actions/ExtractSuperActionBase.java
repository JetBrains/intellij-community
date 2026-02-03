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

package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ExtractSuperActionBase extends BasePlatformRefactoringAction {

  private static final String[] PREFIXES = {"Extract", "Introduce"};

  @Override
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    removeFirstWordInMainMenu(this, e);
  }

  public static void removeFirstWordInMainMenu(@NotNull AnAction action, @NotNull AnActionEvent e) {
    if (e.getPlace().equals(ActionPlaces.MAIN_MENU) || isInToolbarActionGroupWithKnownPrefix(e)) {
      String templateText = action.getTemplatePresentation().getText();
      if (startsWithKnownPrefix(templateText)) {
        e.getPresentation().setText(templateText.substring(templateText.indexOf(' ') + 1));
      }
    }
  }

  private static boolean isInToolbarActionGroupWithKnownPrefix(@NotNull AnActionEvent e) {
    if (!e.getPlace().contains(ActionPlaces.EDITOR_FLOATING_TOOLBAR) && !e.isFromContextMenu()) return false;
    ActionGroup actionGroup = e.getData(ActionGroup.CONTEXT_ACTION_GROUP_KEY);
    return actionGroup != null && startsWithKnownPrefix(actionGroup.getTemplatePresentation().getText());
  }

  private static boolean startsWithKnownPrefix(@Nullable String text) {
    if (text == null) return false;
    for (String prefix : PREFIXES) {
      if (StringUtil.startsWith(text, prefix)) return true;
    }
    return false;
  }
}