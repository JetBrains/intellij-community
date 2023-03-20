/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ide.SmartSelectProvider;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class SmartSelect extends DumbAwareAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    SmartSelectProvider provider = getProvider(e.getDataContext());
    assert provider != null;
    //noinspection unchecked
    provider.increaseSelection(provider.getSource(e.getDataContext()));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean hasSpeedSearch = e.getData(PlatformDataKeys.SPEED_SEARCH_TEXT) != null;
    SmartSelectProvider provider = hasSpeedSearch ? null : getProvider(e.getDataContext());
    if (provider != null) {
      Object source = provider.getSource(e.getDataContext());
      //noinspection unchecked
      if ( (isIncreasing() && provider.canIncreaseSelection(source))
      || (!isIncreasing() && provider.canDecreaseSelection(source))) {
        e.getPresentation().setEnabled(true);
        return;
      }
    }
    e.getPresentation().setEnabled(false);
  }

  protected boolean isIncreasing() {
    return true;
  }

  public SmartSelectProvider getProvider(DataContext context) {
    for (SmartSelectProvider provider : SmartSelectProvider.EP.getExtensions()) {
      if (provider.isEnabled(context)) {
        return provider;
      }
    }
    return null;
  }
}
