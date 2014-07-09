package ru.compscicenter.edide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import ru.compscicenter.edide.StudyEditor;
import ru.compscicenter.edide.StudyTaskManager;
import ru.compscicenter.edide.course.Task;
import ru.compscicenter.edide.course.TaskFile;

/**
 * author: liana
 * data: 7/8/14.
 */
public class NextTaskAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(NextTaskAction.class.getName());

  public void nextTask(Project project) {
    Editor selectedEditor = StudyEditor.getSelectedEditor(project);
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());
    StudyTaskManager taskManager = StudyTaskManager.getInstance(selectedEditor.getProject());
    TaskFile selectedTaskFile = taskManager.getTaskFile(openedFile);
    Task currentTask = selectedTaskFile.getTask();
    Task nextTask = currentTask.next();
    if (nextTask == null) {
      BalloonBuilder balloonBuilder =
        JBPopupFactory.getInstance().createHtmlTextBalloonBuilder("It's the last task", MessageType.INFO, null);
      Balloon balloon = balloonBuilder.createBalloon();
      StudyEditor selectedStudyEditor = StudyEditor.getSelectedStudyEditor(project);
      balloon.showInCenterOf(selectedStudyEditor.getNextTaskButton());
      return;
    }
    for (VirtualFile file : FileEditorManager.getInstance(project).getOpenFiles()) {
      FileEditorManager.getInstance(project).closeFile(file);
    }
    int nextTaskIndex = nextTask.getIndex();
    int lessonIndex = nextTask.getLesson().getIndex();
    TaskFile nextFile = nextTask.getTaskFiles().iterator().next();
    if (nextFile != null) {
      try {

        VirtualFile taskDir =
          project.getBaseDir().findChild("course").findChild("lesson" + String.valueOf(lessonIndex + 1)).findChild("task" + String.valueOf(nextTaskIndex + 1));

        FileEditorManager.getInstance(project).openFile(taskDir.findChild(nextTask.getTaskFiles().iterator().next().getName()), true);
      }
      catch (NullPointerException e) {
        LOG.error("Something wrong with your project structure");
      }
    }
  }

  public void actionPerformed(AnActionEvent e) {
    // TODO: find some free shortcuts :(
    nextTask(e.getProject());
  }
}
