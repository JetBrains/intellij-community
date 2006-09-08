/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.project.Project;

import java.util.ArrayList;

/**
 * User: anna
 * Date: 15-Feb-2006
 */
public class InspectionProfileWrapper {
  private InspectionProfileImpl myProfile;

  public InspectionProfileWrapper(final InspectionProfile profile) {
    myProfile = (InspectionProfileImpl)profile;
  }

  public InspectionTool[] getInspectionTools(){
     return (InspectionTool[])myProfile.getInspectionTools();
  }

  public LocalInspectionTool[] getHighlightingLocalInspectionTools() {
    ArrayList<LocalInspectionTool> enabled = new ArrayList<LocalInspectionTool>();
    final InspectionTool[] tools = getInspectionTools();
    for (InspectionTool tool : tools) {
      if (tool instanceof LocalInspectionToolWrapper) {
        if (myProfile.isToolEnabled(HighlightDisplayKey.find(tool.getShortName()))) {
          enabled.add(((LocalInspectionToolWrapper)tool).getTool());
        }
      }
    }
    return enabled.toArray(new LocalInspectionTool[enabled.size()]);
  }

  public String getName() {
    return myProfile.getName();
  }

  public boolean isToolEnabled(final HighlightDisplayKey key) {
    return myProfile.isToolEnabled(key);
  }

  public InspectionTool getInspectionTool(final String shortName) {
    return (InspectionTool)myProfile.getInspectionTool(shortName);
  }

  public void init(final Project project) {
    final InspectionProfileEntry[] profileEntries = myProfile.getInspectionTools();
    for (InspectionProfileEntry profileEntry : profileEntries) {
      profileEntry.projectOpened(project);
    }
  }

  public void cleanup(final Project project){
    final InspectionProfileEntry[] profileEntries = myProfile.getInspectionTools();
    for (InspectionProfileEntry profileEntry : profileEntries) {
      profileEntry.projectClosed(project);
    }
    myProfile.cleanup();
  }

  public InspectionProfile getInspectionProfile() {
    return myProfile;
  }

  public HighlightDisplayLevel getErrorLevel(final HighlightDisplayKey key) {
    return myProfile.getErrorLevel(key);
  }
}
