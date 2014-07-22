package ru.compscicenter.edide.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import ru.compscicenter.edide.StudyEditor;
import ru.compscicenter.edide.StudyTaskManager;
import ru.compscicenter.edide.course.Task;
import ru.compscicenter.edide.course.TaskFile;
import ru.compscicenter.edide.course.Window;

import java.io.*;

/**
 * User: lia
 * Date: 23.05.14
 * Time: 20:33
 */
public class CheckAction extends AnAction {

  @Override
  public boolean displayTextInToolbar() {
    return false;
  }

  public void check(final Project project) {
    final Editor selectedEditor = StudyEditor.getSelectedEditor(project);
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    final VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());
    StudyTaskManager taskManager = StudyTaskManager.getInstance(selectedEditor.getProject());
    final TaskFile selectedTaskFile = taskManager.getTaskFile(openedFile);
    FileDocumentManager.getInstance().saveAllDocuments();
    if (!(project != null && project.isOpen())) {
      return;
    }
    String basePath = project.getBasePath();
    if (basePath == null) return;
    if (openedFile == null) return;
    VirtualFile taskDir = openedFile.getParent();
    Task currentTask = selectedTaskFile.getTask();
    File testRunner = new File(taskDir.getCanonicalPath(), currentTask.getTestFile());
    String testPath = testRunner.getAbsolutePath();
    GeneralCommandLine cmd = new GeneralCommandLine();
    cmd.setWorkDirectory(taskDir.getCanonicalPath());
    cmd.setExePath("python");
    cmd.addParameter(testPath);
    cmd.addParameter(openedFile.getNameWithoutExtension());
    final int testNum = currentTask.getTestNum();
    int testPassed = 0;
    try {
      Process p = cmd.createProcess();
      InputStream is_err = p.getErrorStream();
      InputStream is = p.getInputStream();
      BufferedReader bf = new BufferedReader(new InputStreamReader(is));
      BufferedReader bf_err = new BufferedReader(new InputStreamReader(is_err));
      StringBuilder errorText = new StringBuilder();
      String line;
      while ((line = bf_err.readLine()) != null) {
        errorText.append(line).append("\n");
      }
      if (errorText.length() > 0) {
        Messages.showErrorDialog(project, errorText.toString(), "Failed to Run");
        currentTask.setFailed(true);
        ProjectView.getInstance(project).refresh();
        return;
      }
      while ((line = bf.readLine()) != null) {
        if (line.equals("#study_plugin test OK")) {
          testPassed++;
        }
      }
      if (testPassed == testNum) {
        currentTask.setSolved(true);
        currentTask.setFailed(false);
        for (TaskFile taskFile : currentTask.getTaskFiles()) {
          for (Window window : taskFile.getWindows()) {
            window.setResolveStatus(true);
          }
        }
        selectedTaskFile.drawAllWindows(selectedEditor);
        ProjectView.getInstance(project).refresh();
        BalloonBuilder balloonBuilder =
          JBPopupFactory.getInstance().createHtmlTextBalloonBuilder("Congratulations!", null, JBColor.GREEN, null);
        Balloon balloon = balloonBuilder.createBalloon();
        StudyEditor selectedStudyEditor = StudyEditor.getSelectedStudyEditor(project);
        balloon.showInCenterOf(selectedStudyEditor.getCheckButton());
      } else {
        String result = String.format("%d from %d tests failed", testNum - testPassed, testNum);
        BalloonBuilder balloonBuilder =
          JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(result, null, JBColor.RED, null);
        Balloon balloon = balloonBuilder.createBalloon();
        StudyEditor selectedStudyEditor = StudyEditor.getSelectedStudyEditor(project);
        balloon.showInCenterOf(selectedStudyEditor.getCheckButton());
      }
    }
    catch (ExecutionException e1) {
      e1.printStackTrace();
    }
    catch (IOException e1) {
      e1.printStackTrace();
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    check(e.getProject());
  }
}
