/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 31-Jul-2006
 * Time: 17:44:39
 */
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.profile.ProfileManager;

import javax.swing.*;

public class InspectionToolsConfigurable implements Configurable {
  private InspectionProfileImpl myInspectionProfile;
  private String mySelectedTool;
  private SingleInspectionProfilePanel myPanel;

  public InspectionToolsConfigurable(final Project project,
                                     final ProfileManager profileManager,
                                     final InspectionProfileImpl inspectionProfile,
                                     final String selectedTool) {
    myInspectionProfile = inspectionProfile;
    mySelectedTool = selectedTool;
    myPanel = new SingleInspectionProfilePanel(myInspectionProfile.getName(), inspectionProfile.getModifiableModel(), project, profileManager);
  }

  public String getDisplayName() {
    return InspectionsBundle.message("errors.single.profile.title", myInspectionProfile.getName());
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableErrorHighlighting.png");
  }

  public String getHelpTopic() {
    return "preferences.errorHighlight";
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return myPanel.isModified();
  }

  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  public void reset() {
    myPanel.reset();
    if (mySelectedTool != null) {
      myPanel.selectInspectionTool(mySelectedTool);
    }
  }

  public void disposeUIResources() {
    if (myPanel != null) {
      myPanel.disposeUI();
      myPanel = null;
    }
  }

  public SingleInspectionProfilePanel getPanel(){
    return myPanel;
  }
}