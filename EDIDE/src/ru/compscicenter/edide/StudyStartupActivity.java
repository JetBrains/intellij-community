package ru.compscicenter.edide;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

/**
 * author: liana
 * data: 7/25/14.
 */
public class StudyStartupActivity implements StartupActivity, DumbAware {


  @Override
  public void runActivity(@NotNull final Project project) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            StudyCondition.myValue = true;
            ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
            ToolWindow window = toolWindowManager.getToolWindow(StudyToolWindowFactory.STUDY_TOOL_WINDOW);
            if (window == null) {
              toolWindowManager.registerToolWindow(StudyToolWindowFactory.STUDY_TOOL_WINDOW,false, ToolWindowAnchor.RIGHT, project, true);
              StudyCondition.myValue = false;
            }

            final ToolWindow newWindow = toolWindowManager.getToolWindow("StudyToolWindow");
            if (newWindow != null) {
              UISettings uiSettings = UISettings.getInstance();
              StudyToolWindowFactory.showStripes = uiSettings.HIDE_TOOL_STRIPES;
              uiSettings.HIDE_TOOL_STRIPES = true;
              uiSettings.fireUISettingsChanged();
              newWindow.show(new Runnable() {
                @Override
                public void run() {
                  StudyToolWindowFactory factory = new StudyToolWindowFactory();
                  factory.createToolWindowContent(project, newWindow);
                }
              });


            }





          }
        });
      }
    });
  }
}
