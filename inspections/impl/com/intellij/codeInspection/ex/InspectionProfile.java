package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.profile.Profile;

/**
 * User: anna
 * Date: Dec 7, 2004
 */
public interface InspectionProfile extends Profile {

  HighlightDisplayLevel getErrorLevel(HighlightDisplayKey inspectionToolKey);

  InspectionProfileEntry getInspectionTool(String shortName);

  InspectionProfileEntry[] getInspectionTools();

  void cleanup();

  ModifiableModel getModifiableModel();

  boolean isToolEnabled(HighlightDisplayKey key);

  boolean isExecutable();

  void save();
}
