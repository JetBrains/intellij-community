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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.settingsSummary.ProblemType;
import com.intellij.settingsSummary.ui.SettingsSummaryDialog;

public class CollectSettingsAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    new SettingsSummaryDialog(e.getRequiredData(CommonDataKeys.PROJECT)).show();
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(e.getProject() != null && ProblemType.EP_SETTINGS.getExtensions().length > 0);
  }
}
