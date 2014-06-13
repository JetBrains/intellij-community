/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;

public class ProjectNewWindowDoNotAskOption implements DialogWrapper.DoNotAskOption {
  public boolean isToBeShown() {
    return true;
  }

  public void setToBeShown(boolean value, int exitCode) {
    int confirmOpenNewProject = value || exitCode == 2 ? GeneralSettings.OPEN_PROJECT_ASK :
                                exitCode == 0 ? GeneralSettings.OPEN_PROJECT_SAME_WINDOW : GeneralSettings.OPEN_PROJECT_NEW_WINDOW ;
    GeneralSettings.getInstance().setConfirmOpenNewProject(confirmOpenNewProject);
  }

  public boolean canBeHidden() {
    return true;
  }

  public boolean shouldSaveOptionsOnCancel() {
    return false;
  }

  @NotNull
  public String getDoNotShowMessage() {
    return CommonBundle.message("dialog.options.do.not.ask");
  }
}
