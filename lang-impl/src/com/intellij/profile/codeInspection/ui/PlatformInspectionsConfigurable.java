package com.intellij.profile.codeInspection.ui;

import com.intellij.psi.PsiFile;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.IconLoader;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

/**
 * @author yole
 */
public class PlatformInspectionsConfigurable implements ErrorsConfigurable {
  private SingleInspectionProfilePanel myPanel;
  private ModifiableModel myProfile;

  @Nls
  public String getDisplayName() {
    return "Inspections";
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableErrorHighlighting.png");
  }

  public JComponent createComponent() {
    return getPanel();
  }

  public boolean isModified() {
    return myPanel != null && myPanel.isModified();
  }

  public void apply() throws ConfigurationException {
    getPanel().apply();
  }

  public void reset() {
    getPanel().reset();
  }

  public void disposeUIResources() {
    if (myPanel != null) {
      myPanel.disposeUI();
      myProfile = null;
      myPanel = null;
    }
  }

  public void selectNodeInTree(final String name) {
  }

  public void selectInspectionTool(final String selectedToolShortName) {
    getPanel().selectInspectionTool(selectedToolShortName);
  }

  public Object getSelectedObject() {
    return null;
  }

  public void selectScopeFor(final PsiFile file) {
  }

  public String getHelpTopic() {
    return null;
  }

  public SingleInspectionProfilePanel getPanel() {
    if (myProfile == null) {
      myProfile = new InspectionProfileImpl((InspectionProfileImpl) InspectionProfileManager.getInstance().getRootProfile());
    }
    if (myPanel == null) {
      myPanel = new SingleInspectionProfilePanel(myProfile.getName(), myProfile);
    }
    return myPanel;
  }
}
