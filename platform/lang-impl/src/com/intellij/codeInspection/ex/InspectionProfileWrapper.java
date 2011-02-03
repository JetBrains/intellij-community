/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import gnu.trove.THashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * User: anna
 * Date: 15-Feb-2006
 */
public class InspectionProfileWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionProfileWrapper");

  /**
   * Key that is assumed to hold strategy that customizes {@link InspectionProfileWrapper} object to use.
   * <p/>
   * I.e. given strategy (if any) receives {@link InspectionProfileWrapper} object that is going to be used so far and returns 
   * {@link InspectionProfileWrapper} object that should be used later.
   */
  public static final Key<Function<InspectionProfileWrapper, InspectionProfileWrapper>> CUSTOMIZATION_KEY
    = Key.create("Inspection Profile Wrapper Customization");
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
    checkInspectionsDuplicates(tools);
    for (InspectionTool tool : tools) {
      if (tool instanceof LocalInspectionToolWrapper) {
        if (myProfile.isToolEnabled(HighlightDisplayKey.find(tool.getShortName()), element)) {
          enabled.add(((LocalInspectionToolWrapper)tool).getTool());
        }
      }
    }
    return enabled;
  }

  // check whether some inspection got registered twice by accident. Bit only once.
  private static boolean alreadyChecked;
  private static void checkInspectionsDuplicates(InspectionTool[] tools) {
    if (alreadyChecked) return;
    alreadyChecked = true;
    Set<InspectionTool> uniqTools = new THashSet<InspectionTool>(tools.length);
    for (InspectionTool tool : tools) {
      if (!uniqTools.add(tool)) {
        LOG.error("Inspection " + tool.getDisplayName() + " (" + tool.getClass() + ") already registered");
      }
    }
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
