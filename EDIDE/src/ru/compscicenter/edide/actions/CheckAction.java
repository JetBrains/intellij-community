package ru.compscicenter.edide.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.SaveAndSyncHandlerImpl;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.ui.JBColor;
import ru.compscicenter.edide.StudyEditor;
import ru.compscicenter.edide.StudyTaskManager;
import ru.compscicenter.edide.course.TaskFile;
import ru.compscicenter.edide.course.Window;
import sun.java2d.loops.ProcessPath;

import java.awt.*;
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

  public void check(Project project) {
    Editor selectedEditor = StudyEditor.getSelectedEditor(project);
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());
    StudyTaskManager taskManager = StudyTaskManager.getInstance(selectedEditor.getProject());
    TaskFile selectedTaskFile = taskManager.getTaskFile(openedFile);
    Window selectedWindow = selectedTaskFile.getSelectedWindow();
    if (selectedWindow!=null)selectedWindow.setResolveStatus(true);
    //selectedTaskFile.setSelectedWindow(null);
    FileDocumentManager.getInstance().saveAllDocuments();
    if (!(project != null && project.isOpen())) {
      return;
    }
    String basePath = project.getBasePath();
    if (basePath == null) return;
    if (selectedEditor == null) {
      return;
    }
    //TODO: replace with platform independent path join
    File testFile = new File(openedFile.getParent().getCanonicalPath(), selectedTaskFile.getTask().getTestFile());
    String testPath = testFile.getAbsolutePath();
    GeneralCommandLine cmd = new GeneralCommandLine();
    cmd.setWorkDirectory(openedFile.getParent().getCanonicalPath());
    cmd.setExePath("python");
    cmd.addParameter(testPath);
    try {
      Process p = cmd.createProcess();
      InputStream is_err = p.getErrorStream();
      InputStream is = p.getInputStream();
      BufferedReader bf = new BufferedReader(new InputStreamReader(is));
      BufferedReader bf_err = new BufferedReader(new InputStreamReader(is_err));
      String line;
      String testResult = "test failed";
      while ((line = bf.readLine()) != null) {
        if (line.equals("OK")) {
          testResult = "test passed";
          ProjectView.getInstance(project).refresh();
          selectedTaskFile.getTask().setSolved(true);
        }
        System.out.println(line);
      }
      while ((line = bf_err.readLine()) != null) {
        if (line.equals("OK")) {
          testResult = "test passed";
          ProjectView.getInstance(project).refresh();
          selectedTaskFile.getTask().setSolved(true);
        }
        System.out.println(line);
      }
      Color myColor = JBColor.RED;
      if (testResult.equals("test passed")) {
        myColor = JBColor.GREEN;
      }
      BalloonBuilder balloonBuilder =
        JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(testResult, null, myColor, null);
      Balloon balloon = balloonBuilder.createBalloon();
      StudyEditor selectedStudyEditor = StudyEditor.getSelectedStudyEditor(project);
      balloon.showInCenterOf(selectedStudyEditor.getCheckButton());
      SaveAndSyncHandlerImpl.refreshOpenFiles();
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
