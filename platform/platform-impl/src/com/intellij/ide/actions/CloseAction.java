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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.IconLoader;

public class CloseAction extends AnAction implements DumbAware {

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setIcon(ActionPlaces.isToolbarPlace(e.getPlace()) ? IconLoader.getIcon("/actions/cancel.png") : null);

    CloseTarget closeTarget = e.getData(CloseTarget.KEY);
    e.getPresentation().setEnabled(closeTarget != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    e.getData(CloseTarget.KEY).close();
  }

  public static interface CloseTarget {

    public static DataKey<CloseTarget> KEY = DataKey.create("GenericClosable");

    void close();
  }
}
