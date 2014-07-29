package ru.compscicenter.edide.actions;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import ru.compscicenter.edide.StudyDocumentListener;
import ru.compscicenter.edide.ui.StudyToolWindowFactory;
import ru.compscicenter.edide.course.*;
import ru.compscicenter.edide.editor.StudyEditor;
import ru.compscicenter.edide.StudyTaskManager;

import java.io.*;

/**
 * author: liana
 * data: 7/8/14.
 */
public class RefreshTaskAction extends AnAction {

  public void refresh(final Project project) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            CommandProcessor.getInstance().executeCommand(project, new Runnable() {
              @Override
              public void run() {
                Editor editor = StudyEditor.getSelectedEditor(project);
                Document document = editor.getDocument();
                StudyDocumentListener listener = StudyEditor.getListener(document);
                if (listener != null) {
                  document.removeDocumentListener(listener);
                }
                document.deleteString(0, document.getLineEndOffset(document.getLineCount() - 1));
                StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
                File resourceFile = new File(taskManager.getCourse().getResourcePath());
                File resourceRoot = resourceFile.getParentFile();
                FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
                VirtualFile openedFile = fileDocumentManager.getFile(document);
                TaskFile selectedTaskFile = taskManager.getTaskFile(openedFile);
                Task currentTask = selectedTaskFile.getTask();
                String lessonDir = Lesson.LESSON_DIR + String.valueOf(currentTask.getLesson().getIndex() + 1);
                String taskDir = Task.TASK_DIR + String.valueOf(currentTask.getIndex() + 1);
                if (openedFile != null) {
                  File pattern = new File(new File(new File(resourceRoot, lessonDir), taskDir), openedFile.getName());
                  BufferedReader reader = null;
                  try {
                    reader = new BufferedReader(new InputStreamReader(new FileInputStream(pattern)));
                    String line;
                    StringBuilder patternText = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                      patternText.append(line);
                      patternText.append("\n");
                    }
                    int patternLength = patternText.length();
                    if (patternText.charAt(patternLength - 1) == '\n') {
                      patternText.delete(patternLength - 1, patternLength);
                    }
                    document.setText(patternText);
                    StudyStatus oldStatus = currentTask.getStatus();
                    LessonInfo lessonInfo = currentTask.getLesson().getLessonInfo();
                    if (oldStatus == StudyStatus.Failed) {
                      lessonInfo.setTaskFailed(lessonInfo.getTaskFailed() - 1);
                    }
                    if (oldStatus == StudyStatus.Solved) {
                      lessonInfo.setTaskSolved(lessonInfo.getTaskSolved() - 1);
                    }
                    lessonInfo.setTaskUnchecked(lessonInfo.getTaskUnchecked() + 1);
                    ToolWindowManager.getInstance(project).getToolWindow(StudyToolWindowFactory.STUDY_TOOL_WINDOW).getContentManager().removeAllContents(false);
                    StudyToolWindowFactory factory =  new StudyToolWindowFactory();
                    factory.createToolWindowContent(project, ToolWindowManager.getInstance(project).getToolWindow(StudyToolWindowFactory.STUDY_TOOL_WINDOW));
                    for (Window window : selectedTaskFile.getWindows()) {
                      window.reset();
                    }
                    ProjectView.getInstance(project).refresh();
                    if (listener != null) {
                      document.addDocumentListener(listener);
                    }
                    selectedTaskFile.drawAllWindows(editor);
                    BalloonBuilder balloonBuilder =
                      JBPopupFactory.getInstance().createHtmlTextBalloonBuilder("You can now start again", MessageType.INFO, null);
                    Balloon balloon = balloonBuilder.createBalloon();
                    StudyEditor selectedStudyEditor = StudyEditor.getSelectedStudyEditor(project);
                    balloon.showInCenterOf(selectedStudyEditor.getRefreshButton());
                  }
                  catch (FileNotFoundException e1) {
                    e1.printStackTrace();
                  }
                  catch (IOException e1) {
                    e1.printStackTrace();
                  }
                  finally {
                    if (reader != null) {
                      try {
                        reader.close();
                      }
                      catch (IOException e) {
                        e.printStackTrace();
                      }
                    }
                  }
                }
              }
            }, null, null);
          }
        });
      }
    });
  }

  public void actionPerformed(AnActionEvent e) {
    refresh(e.getProject());
  }
}
