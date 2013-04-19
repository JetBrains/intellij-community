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
package com.intellij.execution.runners;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

/**
 * @author Roman.Chernyatchik
 */
public class FakeRerunAction extends AnAction implements DumbAware {
  protected static final List<RestartAction> registry = ContainerUtil.createLockFreeCopyOnWriteList();

  @Override
  public void actionPerformed(AnActionEvent e) {
    RestartAction action = RestartAction.findActualAction();
    if (action != null && action.isEnabled()) {
      action.actionPerformed(e);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    RestartAction action = RestartAction.findActualAction();
    presentation.setEnabled(action != null && action.isEnabled());
    presentation.setVisible(false);
  }
}
