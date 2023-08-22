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

package com.intellij.ide.util.scopeChooser;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.packageDependencies.DependencyUISettings;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

public final class FlattenPackagesAction extends ToggleAction {
  private final Runnable myUpdate;

  public FlattenPackagesAction(Runnable update) {
    super(IdeBundle.messagePointer("action.flatten.packages"), PlatformIcons.FLATTEN_PACKAGES_ICON);
    myUpdate = update;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent event) {
    return DependencyUISettings.getInstance().UI_FLATTEN_PACKAGES;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent event, boolean flag) {
    DependencyUISettings.getInstance().UI_FLATTEN_PACKAGES = flag;
    myUpdate.run();
  }
}
