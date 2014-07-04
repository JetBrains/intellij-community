package ru.compscicenter.edide.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import ru.compscicenter.edide.StudyEditor;
import ru.compscicenter.edide.StudyTaskManager;
import ru.compscicenter.edide.course.TaskFile;
import ru.compscicenter.edide.course.Window;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * User: lia
 * Date: 23.05.14
 * Time: 20:33
 */
class CheckAction extends AnAction {

  @Override
  public boolean displayTextInToolbar() {
    return false;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    Editor selectedEditor = StudyEditor.getSelectedEditor(project);
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());
    StudyTaskManager taskManager = StudyTaskManager.getInstance(selectedEditor.getProject());
    TaskFile selectedTaskFile = taskManager.getTaskFile(openedFile);
    Window selectedWindow = selectedTaskFile.getSelectedWindow();
    if (selectedWindow!=null)selectedWindow.setResolveStatus(true);
    selectedTaskFile.setSelectedWindow(null);
        FileDocumentManager.getInstance().saveAllDocuments();
                if (!(project != null && project.isOpen())) {
          return;
        }
        String basePath = project.getBasePath();
        if (basePath == null) return;
        if (selectedEditor == null) {
          return;
        }
        VirtualFile vfOpenedFile = FileDocumentManager.getInstance().getFile(selectedEditor.getDocument());
        //TODO: replace with platform independent path join
        String testFile = basePath +
                          "/.idea/study-tests/" + selectedTaskFile.getTask().getTestFile();
        GeneralCommandLine cmd = new GeneralCommandLine();
        cmd.setWorkDirectory(basePath + "/.idea/study-tests/");
        cmd.setExePath("python");
        cmd.addParameter(testFile);
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
            }
            System.out.println(line);
          }
          while ((line = bf_err.readLine()) != null) {
            if (line.equals("OK")) {
              testResult = "test passed";
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
         //TODO:get check button coordinates
          RelativePoint rp  = new RelativePoint(new Point(200, 40));
          //Component component = e.getInputEvent().getComponent();
          //RelativePoint rp  = new RelativePoint(new Point(component.getX(), component.getY()));
            balloon.show(rp, Balloon.Position.above);
          //JOptionPane.showMessageDialog(null, testResult, "", JOptionPane.INFORMATION_MESSAGE);
        }
        catch (ExecutionException e1) {
          e1.printStackTrace();
        }
        catch (IOException e1) {
          e1.printStackTrace();
        }
  }
}
