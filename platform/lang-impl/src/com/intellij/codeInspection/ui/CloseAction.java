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
package com.intellij.codeInspection.ui;

import com.intellij.CommonBundle;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;

class CloseAction extends AnAction implements DumbAware {
  private final GlobalInspectionContextImpl myContext;

  CloseAction(GlobalInspectionContextImpl context) {
    super(CommonBundle.message("action.close"), null, AllIcons.Actions.Cancel);
    myContext = context;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    myContext.close(true);
  }
}
