package ru.compscicenter.edide.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import icons.StudyIcons;
import org.jetbrains.annotations.NotNull;
import ru.compscicenter.edide.StudyTaskManager;
import ru.compscicenter.edide.course.*;

/**
 * author: liana
 * data: 6/25/14.
 */

public class StudyDirectoryNode extends PsiDirectoryNode {
  private PsiDirectory myValue;
  private Project myProject;

  public StudyDirectoryNode(@NotNull final Project project,
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
    StudyTaskManager studyTaskManager = StudyTaskManager.getInstance(myProject);
    if (valueName.contains(Task.TASK_DIR)) {
      TaskFile file = null;
      for (PsiElement child : myValue.getChildren()) {
        VirtualFile virtualFile = child.getContainingFile().getVirtualFile();
        file = studyTaskManager.getTaskFile(virtualFile);
        if (file != null) {
          break;
        }
      }
      if (file != null) {
        StudyStatus taskStatus = file.getTask().getStatus();
        if (taskStatus == StudyStatus.Failed) {
          data.setIcon(StudyIcons.FailedTask);
        }
        if (taskStatus == StudyStatus.Solved) {
          data.setIcon(StudyIcons.CheckedTask);
        }
      }
    }

    Course course = studyTaskManager.getCourse();
    if (course == null) {
      return;
    }
    if (valueName.contains(Lesson.LESSON_DIR)) {
      int lessonIndex = Integer.parseInt(valueName.substring(Lesson.LESSON_DIR.length())) - 1;
      Lesson lesson = course.getLessons().get(lessonIndex);
      if (lesson.getStatus() == StudyStatus.Solved) {
        data.setIcon(StudyIcons.CheckedTask);
      }
    }

    if (valueName.contains(Course.PLAYGROUND_DIR)) {
      if (myValue.getParent() != null) {
        if (!myValue.getParent().getName().contains(Course.PLAYGROUND_DIR)) {
          data.setPresentableText(Course.PLAYGROUND_DIR);
          data.setIcon(StudyIcons.Playground);
          return;
        }
      }
    }
    PsiDirectory parent = myValue.getParent();
    if (parent != null) {
      if (myProject.getName().equals(parent.getName()) && valueName.contains(Course.COURSE_DIR)) {
        data.setPresentableText(course.getName());
        return;
      }
    }
    data.setPresentableText(valueName);
  }

  @Override
  public int getTypeSortWeight(boolean sortByType) {
    return myValue.getName().contains(Course.PLAYGROUND_DIR)? 0 : 3;
  }
}
