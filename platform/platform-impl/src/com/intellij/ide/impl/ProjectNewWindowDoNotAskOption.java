// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DoNotAskOption;
import org.jetbrains.annotations.NotNull;

public final class ProjectNewWindowDoNotAskOption implements DoNotAskOption {
  @Override
  public boolean isToBeShown() {
    return true;
  }

  @Override
  public void setToBeShown(boolean value, int exitCode) {
    int mode;
    if (value) {
      mode = GeneralSettings.OPEN_PROJECT_ASK;
    }
    else {
      // see `ProjectUtil#confirmOpenNewProject` and `ProjectUtil#confirmOpenOrAttachProject`
      mode = exitCode == 0 /*Messages.YES*/ ? GeneralSettings.OPEN_PROJECT_SAME_WINDOW :
             exitCode == 1 /*Messages.NO*/ ? GeneralSettings.OPEN_PROJECT_NEW_WINDOW :
             GeneralSettings.OPEN_PROJECT_ASK;
    }
    GeneralSettings.getInstance().setConfirmOpenNewProject(mode);
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
  public @NotNull String getDoNotShowMessage() {
    return IdeCoreBundle.message("dialog.options.do.not.ask");
  }
}
