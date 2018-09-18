// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;

public class ProjectNewWindowDoNotAskOption implements DialogWrapper.DoNotAskOption {
  @Override
  public boolean isToBeShown() {
    return true;
  }

  @Override
  public void setToBeShown(boolean value, int exitCode) {
    int confirmOpenNewProject = value || exitCode == 2 ? GeneralSettings.OPEN_PROJECT_ASK :
                                exitCode == 0 ? GeneralSettings.OPEN_PROJECT_SAME_WINDOW : GeneralSettings.OPEN_PROJECT_NEW_WINDOW ;
    GeneralSettings.getInstance().setConfirmOpenNewProject(confirmOpenNewProject);
  }

  @Override
  public boolean canBeHidden() {
    return true;
  }

  @Override
  public boolean shouldSaveOptionsOnCancel() {
    return false;
  }

  @Override
  @NotNull
  public String getDoNotShowMessage() {
    return CommonBundle.message("dialog.options.do.not.ask");
  }
}
