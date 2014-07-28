package ru.compscicenter.edide;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * author: liana
 * data: 7/25/14.
 */

public class StudyToolWindowFactory implements ToolWindowFactory, DumbAware {
  public static final String STUDY_TOOL_WINDOW = "StudyToolWindow";
  JPanel panel = new JPanel(new BorderLayout());
  JButton nextTask = new JButton("next");
  public static boolean showStripes;

  @Override
  public void createToolWindowContent(final Project project, final ToolWindow toolWindow) {

    panel.add(nextTask, BorderLayout.SOUTH);
    nextTask.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ToolWindow window  = ToolWindowManager.getInstance(project).getToolWindow("StudyToolWindow");
        if (window != null) {
          window.hide(null);
          UISettings uiSettings = UISettings.getInstance();
          uiSettings.HIDE_TOOL_STRIPES = StudyToolWindowFactory.showStripes;
          uiSettings.fireUISettingsChanged();
          ToolWindowManager.getInstance(project).unregisterToolWindow("StudyToolWindow");
          StudyCondition.myValue = false;
        }
      }
    });
    expand(project, toolWindow);
    panel.updateUI();
    ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    Content content = contentFactory.createContent(panel, "", true);
    toolWindow.getContentManager().addContent(content);
  }

  public void expand(Project project, ToolWindow toolWindow) {
    final IdeFrameImpl frame = WindowManagerEx.getInstanceEx().getFrame(project);
    int rootWidth = frame.getWidth();
    int currentSize = toolWindow.getComponent().getWidth();
    ((ToolWindowImpl)toolWindow).stretchWidth(rootWidth - currentSize);
  }
}
