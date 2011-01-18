package com.intellij.ide.scriptingContext.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.ui.HyperlinkLabel;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

/**
 * @author Rustam Vishnyakov
 */
public class ScriptingLibrariesPanelStub {
  private JPanel myMainPanel;
  private HyperlinkLabel myLibrarySettingsLink;
  protected Project myProject;
  
  public ScriptingLibrariesPanelStub(Project project) {
    myProject = project;
  }

  public JComponent getPanel() {
    return myMainPanel;
  }


  public boolean isModified() {
    return false;
  }

  public void resetTable() {
    // Do nothing
  }

  private void createUIComponents() {
    myLibrarySettingsLink = new HyperlinkLabel("Configure global JavaScript libraries (Project Settings/Global Libraries)");
    myLibrarySettingsLink.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          ProjectSettingsService.getInstance(myProject).openProjectSettings();
        }
      }
    });
  }
}
