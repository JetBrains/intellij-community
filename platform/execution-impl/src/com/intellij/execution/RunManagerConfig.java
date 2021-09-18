// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class RunManagerConfig {
  public static final int MIN_RECENT_LIMIT = 0;
  public static final int DEFAULT_RECENT_LIMIT = 5;

  private final PropertiesComponent myPropertiesComponent;

  @NonNls private static final String RECENTS_LIMIT = "recentsLimit";
  @NonNls private static final String RESTART_REQUIRES_CONFIRMATION = "restartRequiresConfirmation";
  @NonNls private static final String DELETION_FROM_POPUP_REQUIRES_CONFIRMATION = "deletionFromPopupRequiresConfirmation";
  @NonNls private static final String STOP_INCOMPATIBLE_REQUIRES_CONFIRMATION = "stopIncompatibleRequiresConfirmation";

  @NonNls private static final String RECENTS_LIMIT_KEY = "temporary.configurations.limit";
  @NonNls private static final String CONFIRM_RERUN_KEY = "confirm.rerun.with.termination";

  public RunManagerConfig(@NotNull PropertiesComponent propertiesComponent) {
    myPropertiesComponent = propertiesComponent;
  }

  public int getRecentsLimit() {
    return AdvancedSettings.getInt(RECENTS_LIMIT_KEY);
  }

  public void setRecentsLimit(int recentsLimit) {
    AdvancedSettings.setInt(RECENTS_LIMIT_KEY, Math.max(MIN_RECENT_LIMIT, recentsLimit));
  }

  public void migrateToAdvancedSettings() {
    if (myPropertiesComponent.isValueSet(RECENTS_LIMIT)) {
      setRecentsLimit(Math.max(MIN_RECENT_LIMIT, StringUtil.parseInt(myPropertiesComponent.getValue(RECENTS_LIMIT), DEFAULT_RECENT_LIMIT)));
      myPropertiesComponent.unsetValue(RECENTS_LIMIT);
    }
    if (myPropertiesComponent.isValueSet(RESTART_REQUIRES_CONFIRMATION)) {
      setRestartRequiresConfirmation(myPropertiesComponent.getBoolean(RESTART_REQUIRES_CONFIRMATION));
      myPropertiesComponent.unsetValue(RESTART_REQUIRES_CONFIRMATION);
    }
  }

  public boolean isRestartRequiresConfirmation() {
    return AdvancedSettings.getBoolean(CONFIRM_RERUN_KEY);
  }

  public void setRestartRequiresConfirmation(boolean restartRequiresConfirmation) {
    AdvancedSettings.setBoolean(CONFIRM_RERUN_KEY, restartRequiresConfirmation);
  }

  public boolean isDeletionFromPopupRequiresConfirmation() {
    return myPropertiesComponent.getBoolean(DELETION_FROM_POPUP_REQUIRES_CONFIRMATION, true);
  }

  public void setDeletionFromPopupRequiresConfirmation(boolean deletionFromPopupRequiresConfirmation) {
    myPropertiesComponent.setValue(DELETION_FROM_POPUP_REQUIRES_CONFIRMATION, deletionFromPopupRequiresConfirmation, true);
  }

  public boolean isStopIncompatibleRequiresConfirmation() {
    return myPropertiesComponent.getBoolean(STOP_INCOMPATIBLE_REQUIRES_CONFIRMATION, true);
  }

  public void setStopIncompatibleRequiresConfirmation(boolean stopIncompatibleRequiresConfirmation) {
    myPropertiesComponent.setValue(STOP_INCOMPATIBLE_REQUIRES_CONFIRMATION, stopIncompatibleRequiresConfirmation, true);
  }
}
