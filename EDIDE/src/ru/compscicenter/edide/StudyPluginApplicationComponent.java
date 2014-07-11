package ru.compscicenter.edide;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import org.jetbrains.annotations.NotNull;

/**
 * author: liana
 * data: 7/11/14.
 */
public class StudyPluginApplicationComponent implements ApplicationComponent {
  public StudyPluginApplicationComponent() {
  }

  public void initComponent() {
    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      @Override
      public boolean canCloseProject(Project project) {
        FileEditorManagerImpl.getInstanceEx(project).closeAllFiles();
        return super.canCloseProject(project);
      }
    });
  }

  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return "StudyPluginApplicationComponent";
  }
}
