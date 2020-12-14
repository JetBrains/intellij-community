// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.ui.EditorNotificationPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface UnknownSdkFix {
  @NotNull
  Project getProject();

  @NotNull
  SdkType getSdkType();

  @Nls @NotNull
  String getNotificationText();

  @Nls
  @Nullable
  String getSdkTypeAndNameText();

  @Nullable
  UnknownSdkFixAction getSuggestedFixAction();

  /**
   * @return default problem specification message
   */
  @Nls
  @NotNull
  String getIntentionActionText();

  @Nls
  @NotNull
  String getConfigureActionText();

  @NotNull
  EditorNotificationPanel.ActionHandler getConfigureActionHandler();
}
