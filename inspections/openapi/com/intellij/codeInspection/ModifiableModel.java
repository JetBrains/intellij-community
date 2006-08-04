/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.profile.Profile;
import com.intellij.profile.ProfileManager;

/**
 * User: anna
 * Date: 15-Feb-2006
 */
public interface ModifiableModel extends Profile {

  InspectionProfile getParentProfile();

  String getBaseProfileName();

  void setBaseProfile(InspectionProfile profile);

  void patchTool(InspectionProfileEntry tool);

  void enableTool(String inspectionTool);

  void disableTool(String inspectionTool);

  void setErrorLevel(HighlightDisplayKey key, HighlightDisplayLevel level);

  HighlightDisplayLevel getErrorLevel(HighlightDisplayKey inspectionToolKey);

  boolean isToolEnabled(HighlightDisplayKey key);

  void commit(final ProfileManager profileManager);

  boolean isChanged();

  void setModified(final boolean toolsSettingsChanged);

  boolean isProperSetting(HighlightDisplayKey key);

  void resetToBase();

  InspectionProfileEntry getInspectionTool(String shortName);

  InspectionProfileEntry[] getInspectionTools();

  void copyFrom(InspectionProfile profile);

  void inheritFrom(InspectionProfile profile);

  boolean isDefault();

  void initInspectionTools();

  boolean isExecutable();

  void setEditable(String toolDisplayName);

  void save();

  boolean isProfileLocked();

  void lockProfile(boolean isLocked);
}
