/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;

import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 15-Feb-2006
 */
public class InspectionProfileWrapper {
  private final InspectionProfileImpl myProfile;

  public InspectionProfileWrapper(final InspectionProfile profile) {
    myProfile = (InspectionProfileImpl)profile;
  }

  public InspectionTool[] getInspectionTools(PsiElement element){
     return (InspectionTool[])myProfile.getInspectionTools(element);
  }

  public List<LocalInspectionTool> getHighlightingLocalInspectionTools(PsiElement element) {
    List<LocalInspectionTool> enabled = new ArrayList<LocalInspectionTool>();
    final InspectionTool[] tools = getInspectionTools(element);
    for (InspectionTool tool : tools) {
      if (tool instanceof LocalInspectionToolWrapper) {
        if (myProfile.isToolEnabled(HighlightDisplayKey.find(tool.getShortName()), element)) {
          enabled.add(((LocalInspectionToolWrapper)tool).getTool());
        }
      }
    }
    return enabled;
  }

  public String getName() {
    return myProfile.getName();
  }

  public boolean isToolEnabled(final HighlightDisplayKey key, PsiElement element) {
    return myProfile.isToolEnabled(key, element);
  }

  public boolean isToolEnabled(final HighlightDisplayKey key) {
    return myProfile.isToolEnabled(key);
  }

  public InspectionTool getInspectionTool(final String shortName, PsiElement element) {
    return (InspectionTool)myProfile.getInspectionTool(shortName, element);
  }

  public void init(final Project project) {
    final List<ToolsImpl> profileEntries = myProfile.getAllEnabledInspectionTools();
    for (Tools profileEntry : profileEntries) {
      for (ScopeToolState toolState : profileEntry.getTools()) {
        toolState.getTool().projectOpened(project);
      }
    }
  }

  public void cleanup(final Project project){

    myProfile.cleanup(project);
  }

  public InspectionProfile getInspectionProfile() {
    return myProfile;
  }


}
