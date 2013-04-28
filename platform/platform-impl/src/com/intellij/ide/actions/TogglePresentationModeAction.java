package com.intellij.ide.actions;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.impl.IdeFrameImpl;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class TogglePresentationModeAction extends AnAction implements DumbAware {
  @Override
  public void update(AnActionEvent e) {
    boolean selected = UISettings.getInstance().PRESENTATION_MODE;
    e.getPresentation().setText(selected ? "Exit Presentation Mode" : "Enter Presentation Mode");
  }

  @Override
  public void actionPerformed(AnActionEvent e){
    UISettings settings = UISettings.getInstance();
    Project project = e.getProject();

    if (project != null) {
      HideAllToolWindowsAction.performAction(project);
    }

    settings.PRESENTATION_MODE = !settings.PRESENTATION_MODE;
    settings.fireUISettingsChanged();

    if (project != null) {
      Window frame = IdeFrameImpl.getActiveFrame();
      if (frame instanceof IdeFrameImpl) {
        ((IdeFrameImpl)frame).toggleFullScreen(true);
      }
    }
  }
}
