// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actionsOnSave;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;

/**
 * This object is created when {@link ActionsOnSaveConfigurable#reset()} is called. It happens in two cases: when the 'Actions on Save'
 * page is opened for the first time during the current Settings (Preferences) dialog session, and also when the 'Reset' link in the
 * top-right corner of the 'Actions on Save' page is clicked.
 * <br/><br/>
 * {@link ActionOnSaveInfo} implementations use this object to understand their state at the moment of creation. Instances of
 * <code>ActionOnSaveInfo</code> have a different lifecycle, see {@link ActionOnSaveInfoProvider#getActionOnSaveInfos(ActionOnSaveContext)}.
 *
 * @see ActionOnSaveInfoProvider#getActionOnSaveInfos(ActionOnSaveContext)
 * @see ActionOnSaveInfo
 */
public final class ActionOnSaveContext extends UserDataHolderBase {

  private final @NotNull Project myProject;
  private final @NotNull Settings mySettings;
  private final @NotNull Disposable mySettingsDialogDisposable;

  ActionOnSaveContext(@NotNull Project project,
                      @NotNull Settings settings,
                      @NotNull Disposable settingsDialogDisposable) {
    myProject = project;
    mySettings = settings;
    mySettingsDialogDisposable = settingsDialogDisposable;
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  public @NotNull Settings getSettings() {
    return mySettings;
  }

  public @NotNull Disposable getSettingsDialogDisposable() {
    return mySettingsDialogDisposable;
  }
}
