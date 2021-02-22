/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.ActionGroupUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.UpdateInBackground;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

/**
 * This group turns itself into a popup if there's more than one child.
 *
 * @see NonEmptyActionGroup
 * @see NonTrivialActionGroup
 * @author yole
 */
public class SmartPopupActionGroup extends DefaultActionGroup implements DumbAware, UpdateInBackground {

  private boolean myCachedIsPopup = true;

  @Override
  public boolean isPopup() {
    return myCachedIsPopup; // called after update()
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    int size = ActionGroupUtil.getActiveActions(this, e).take(3).size();
    e.getPresentation().setEnabledAndVisible(size > 0);
    myCachedIsPopup = size > 2;
  }

  @Override
  public boolean disableIfNoVisibleChildren() {
    return false; // optimization
  }
}
