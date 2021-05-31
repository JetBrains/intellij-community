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

  public RunManagerConfig(@NotNull PropertiesComponent propertiesComponent) {
    myPropertiesComponent = propertiesComponent;
  }

  public int getRecentsLimit() {
    return AdvancedSettings.getInt(RECENTS_LIMIT_KEY);
  }

  public void setRecentsLimit(int recentsLimit) {
    AdvancedSettings.setInt(RECENTS_LIMIT_KEY, recentsLimit);
  }

  public void migrateToRegistry() {
    String value = myPropertiesComponent.getValue(RECENTS_LIMIT);
    if (value != null) {
      setRecentsLimit(Math.max(MIN_RECENT_LIMIT, StringUtil.parseInt(value, DEFAULT_RECENT_LIMIT)));
      myPropertiesComponent.setValue(RECENTS_LIMIT, null);
    }
  }

  public boolean isRestartRequiresConfirmation() {
    return myPropertiesComponent.getBoolean(RESTART_REQUIRES_CONFIRMATION, true);
  }

  public void setRestartRequiresConfirmation(boolean restartRequiresConfirmation) {
    myPropertiesComponent.setValue(RESTART_REQUIRES_CONFIRMATION, restartRequiresConfirmation, true);
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
