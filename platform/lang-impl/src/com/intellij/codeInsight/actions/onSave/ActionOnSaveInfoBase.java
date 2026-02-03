// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions.onSave;

import com.intellij.ide.actionsOnSave.ActionOnSaveContext;
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * This class can be used for 'actions on save' that can be enabled/disabled only on the 'Actions on Save' page in Settings (Preferences).
 * The state of the feature (enabled or disabled) is stored in the project-level {@link PropertiesComponent}.
 */
public abstract class ActionOnSaveInfoBase extends ActionOnSaveInfo {
  private static final Map<String, Key<Boolean>> ourClassNameToKeyMap = new HashMap<>();

  private final @NlsContexts.Checkbox @NotNull String myActionOnSaveName;
  private final @NotNull String myPropertyForRunOnSaveStateStoring;
  private final boolean myRunOnSaveDefault;

  private final Key<Boolean> myKeyForUiStateStoring;

  public ActionOnSaveInfoBase(@NotNull ActionOnSaveContext context,
                              @NotNull @NlsContexts.Checkbox String actionOnSaveName,
                              @NotNull String propertyForRunOnSaveStateStoring,
                              boolean runOnSaveDefault) {
    super(context);
    myActionOnSaveName = actionOnSaveName;
    myPropertyForRunOnSaveStateStoring = propertyForRunOnSaveStateStoring;
    myRunOnSaveDefault = runOnSaveDefault;

    Key<Boolean> key = ourClassNameToKeyMap.get(getClass().getName());
    if (key == null) {
      key = Key.create(getClass().getName());
      ourClassNameToKeyMap.put(getClass().getName(), key);
    }
    myKeyForUiStateStoring = key;
  }

  @Override
  public @NotNull String getActionOnSaveName() {
    return myActionOnSaveName;
  }

  private boolean isActionOnSaveEnabledAccordingToStoredState() {
    return PropertiesComponent.getInstance(getProject()).getBoolean(myPropertyForRunOnSaveStateStoring, myRunOnSaveDefault);
  }

  @Override
  public boolean isActionOnSaveEnabled() {
    Boolean enabledInUi = getContext().getUserData(myKeyForUiStateStoring);
    return enabledInUi != null ? enabledInUi : isActionOnSaveEnabledAccordingToStoredState();
  }

  @Override
  public void setActionOnSaveEnabled(boolean enabled) {
    getContext().putUserData(myKeyForUiStateStoring, enabled);
  }

  @Override
  protected void apply() {
    PropertiesComponent.getInstance(getProject()).setValue(myPropertyForRunOnSaveStateStoring, isActionOnSaveEnabled(), myRunOnSaveDefault);
  }

  @Override
  protected boolean isModified() {
    if (isActionOnSaveEnabledAccordingToStoredState() != isActionOnSaveEnabled()) return true;
    return false;
  }
}
