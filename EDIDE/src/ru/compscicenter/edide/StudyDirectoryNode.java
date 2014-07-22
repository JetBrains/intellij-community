package ru.compscicenter.edide;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import icons.StudyIcons;
import ru.compscicenter.edide.course.Course;
import ru.compscicenter.edide.course.Lesson;
import ru.compscicenter.edide.course.Task;
import ru.compscicenter.edide.course.TaskFile;

/**
 * author: liana
 * data: 6/25/14.
 */

public class StudyDirectoryNode extends PsiDirectoryNode {
  private PsiDirectory myValue;
  private Project myProject;
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
    String valueName = myValue.getName();
    if (valueName.contains(Task.TASK_DIR)) {
      TaskFile file = null;
      for (PsiElement child : myValue.getChildren()) {
        VirtualFile virtualFile = child.getContainingFile().getVirtualFile();
        file = StudyTaskManager.getInstance(myProject).getTaskFile(virtualFile);
        if (file != null) {
          break;
        }
      }
      if (file != null) {
        if (file.getTask().isSolved()) {
          data.setIcon(StudyIcons.CheckedTask);
        }
        if (file.getTask().isFailed()) {
          data.setIcon(StudyIcons.FailedTask);
        }
      }
    }

    if (valueName.contains(Lesson.LESSON_DIR)) {
      int lessonIndex = Integer.parseInt(valueName.substring(Lesson.LESSON_DIR.length())) - 1;
      if (StudyTaskManager.getInstance(myProject).getCourse().getLessons().get(lessonIndex).isSolved()) {
        data.setIcon(StudyIcons.CheckedTask);
      }
    }

    if (valueName.contains("zPlayground")) {
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
      if (myProject.getName().equals(parent.getName()) && valueName.contains(Course.COURSE_DIR)) {
        data.setPresentableText(StudyTaskManager.getInstance(myProject).getCourse().getName());
        return;
      }
    }
    data.setPresentableText(valueName);


  }
}
