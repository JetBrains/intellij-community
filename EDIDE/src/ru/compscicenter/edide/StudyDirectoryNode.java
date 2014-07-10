package ru.compscicenter.edide;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import icons.StudyIcons;

import javax.swing.*;

/**
 * author: liana
 * data: 6/25/14.
 */
public class StudyDirectoryNode extends PsiDirectoryNode {
  PsiDirectory myValue;
  Project myProject;
  public StudyDirectoryNode(Project project,
                            PsiDirectory value,
                            ViewSettings viewSettings) {
    super(project, value, viewSettings);
    myValue = value;
    myProject = project;

  }

  //TODO:improve
  @Override
  protected void updateImpl(PresentationData data) {
    data.setIcon(StudyIcons.UncheckedTask);
    if (myValue.getName().contains("task")) {
      String dirName = myValue.getName();
      if (dirName.contains("task1") && myValue.getChildren().length != 0) {
        if (StudyTaskManager.getInstance(myProject).getCourse().getLessons().get(0).getTaskList().get(0).isResolved()) {
          data.setIcon(StudyIcons.CheckedTask);
        }
      }
    }

    if (myValue.getName().contains("zPlayground")) {
      if (myValue.getParent()!=null) {
        if (!myValue.getParent().getName().contains("zPlayground")) {
          data.setPresentableText("Playground");
          data.setIcon(StudyIcons.Playground);
          return;
        }
      }
    }
    PsiDirectory parent = myValue.getParent();
    if (parent != null) {
      if (myProject.getName().equals(parent.getName()) && myValue.getName().contains("course")) {
        data.setPresentableText(StudyTaskManager.getInstance(myProject).getCourse().getName());
      }
      else {
        data.setPresentableText(myValue.getName());
      }
    }
  }
}
