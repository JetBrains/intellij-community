// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actionsOnSave;

import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * This object is created when {@link ActionsOnSaveConfigurable#reset()} is called. It happens in two cases: when the 'Actions on Save'
 * page is opened for the first time during the current Settings (Preferences) dialog session, and also when the 'Reset' link in the
 * top-right corner of the 'Actions on Save' page is clicked.
 * <br/><br/>
 * {@link ActionOnSaveInfo} implementations use this object to understand their state at the moment of creation. Instances of
 * <code>ActionOnSaveInfo</code> have a different lifecycle, see {@link ActionOnSaveInfoProvider#getActionOnSaveInfos(Project, ActionOnSaveContext)}.
 *
 * @see ActionOnSaveInfoProvider#getActionOnSaveInfos(Project, ActionOnSaveContext)
 * @see ActionOnSaveInfo
 */
@ApiStatus.Experimental
public final class ActionOnSaveContext extends UserDataHolderBase {

  private final @NotNull Project myProject;
  private final @NotNull Settings mySettings;

  ActionOnSaveContext(@NotNull Project project, @NotNull Settings settings) {
    myProject = project;
    mySettings = settings;
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  public @NotNull Settings getSettings() {
    return mySettings;
  }
}
