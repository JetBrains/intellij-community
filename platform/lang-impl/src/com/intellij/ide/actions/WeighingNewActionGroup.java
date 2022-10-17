/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.text.TextWithMnemonic;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class WeighingNewActionGroup extends WeighingActionGroup implements DumbAware {
  private ActionGroup myDelegate;

  @Override
  protected ActionGroup getDelegate() {
    if (myDelegate == null) {
      myDelegate = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_NEW);
    }
    return myDelegate;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Supplier<? extends TextWithMnemonic> prev = e.getPresentation().getTextWithPossibleMnemonic();
    super.update(e);
    if (e.getPresentation().getTextWithPossibleMnemonic() != prev) {
      e.getPresentation().setTextWithMnemonic(prev);
    }
  }

  @Override
  protected boolean shouldBeChosenAnyway(AnAction action) {
    final Class<? extends AnAction> aClass = action.getClass();
    return aClass == CreateFileAction.class || aClass == CreateDirectoryOrPackageAction.class ||
           "NewModuleInGroupAction".equals(aClass.getSimpleName());
  }
}
