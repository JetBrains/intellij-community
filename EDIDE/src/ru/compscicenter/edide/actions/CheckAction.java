package ru.compscicenter.edide.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.jetbrains.python.sdk.PythonSdkType;
import ru.compscicenter.edide.StudyDocumentListener;
import ru.compscicenter.edide.StudyTaskManager;
import ru.compscicenter.edide.StudyUtils;
import ru.compscicenter.edide.course.StudyStatus;
import ru.compscicenter.edide.course.Task;
import ru.compscicenter.edide.course.TaskFile;
import ru.compscicenter.edide.course.TaskWindow;
import ru.compscicenter.edide.editor.StudyEditor;

import javax.swing.*;
import java.awt.*;
import java.io.*;

/**
 * User: lia
 * Date: 23.05.14
 * Time: 20:33
 */
public class CheckAction extends AnAction {

  public static final Logger LOG = Logger.getInstance(CheckAction.class.getName());

  class StudyTestRunner {
    private static final String TEST_OK = "#study_plugin test OK";
    private final Task myTask;
    private final VirtualFile myTaskDir;

    StudyTestRunner(Task task, VirtualFile taskDir) {
      myTask = task;
      myTaskDir = taskDir;
    }

    Process launchTests(Project project, String executablePath) throws ExecutionException {
      Sdk sdk = PythonSdkType.findPythonSdk(ModuleManager.getInstance(project).getModules()[0]);
      File testRunner = new File(myTaskDir.getPath(), myTask.getTestFile());
      GeneralCommandLine commandLine = new GeneralCommandLine();
      commandLine.setWorkDirectory(myTaskDir.getPath());
      if (sdk != null) {
        String pythonPath = sdk.getHomePath();
        if (pythonPath != null) {
          commandLine.setExePath(pythonPath);
          commandLine.addParameter(testRunner.getPath());
          commandLine.addParameter(executablePath);
          return commandLine.createProcess();
        }
      }
      return null;
    }

    int getPassedTests(Process p) {
      int testPassed = 0;
      InputStream testOutput = p.getInputStream();
      BufferedReader testOutputReader = new BufferedReader(new InputStreamReader(testOutput));
      String line;
      try {
        while ((line = testOutputReader.readLine()) != null) {
          if (line.equals(TEST_OK)) {
            testPassed++;
          }
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
      finally {
        StudyUtils.closeSilently(testOutputReader);
      }
      return testPassed;
    }

    boolean testsPassed(Process p) {
      return getPassedTests(p) == myTask.getTestNum();
    }

    String getRunFailedMessage(Process p) {
      InputStream testOutput = p.getErrorStream();
      BufferedReader testOutputReader = new BufferedReader(new InputStreamReader(testOutput));
      StringBuilder errorText = new StringBuilder();
      String line;
      try {
        while ((line = testOutputReader.readLine()) != null) {
          errorText.append(line).append("\n");
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
      finally {
        StudyUtils.closeSilently(testOutputReader);
      }
      return errorText.toString();
    }
  }

  public void check(final Project project) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          @Override
          public void run() {
            final Editor selectedEditor = StudyEditor.getSelectedEditor(project);
            if (selectedEditor == null) {
              return;
            }
            final FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
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
            final VirtualFile taskDir = openedFile.getParent();
            Task currentTask = selectedTaskFile.getTask();
            final StudyTestRunner testRunner = new StudyTestRunner(currentTask, taskDir);
            Process testProcess = null;
            try {
              testProcess = testRunner.launchTests(project, openedFile.getNameWithoutExtension());
            }
            catch (ExecutionException e) {
              LOG.error(e);
            }
            if (testProcess != null) {
              final int testNum = currentTask.getTestNum();
              final int testPassed = testRunner.getPassedTests(testProcess);
              if (testPassed == testNum) {
                currentTask.setStatus(StudyStatus.Solved);
                StudyUtils.updateStudyToolWindow(project);
                selectedTaskFile.drawAllWindows(selectedEditor);
                ProjectView.getInstance(project).refresh();
                createTestResultPopUp("Congratulations!", JBColor.GREEN, project);
                return;
              }

              final TaskFile taskFileCopy = new TaskFile();
              final VirtualFile copyWithAnswers = getCopyWithAnswers(taskDir, openedFile, selectedTaskFile, taskFileCopy);
              for (final TaskWindow taskWindow : taskFileCopy.getTaskWindows()) {
                check(project, taskWindow, copyWithAnswers, taskFileCopy, selectedTaskFile, selectedEditor.getDocument(), testRunner);
              }
              try {
                copyWithAnswers.delete(this);
              }
              catch (IOException e) {
                LOG.error(e);
              }
              if (testPassed == 0) {
                String message = testRunner.getRunFailedMessage(testProcess);
                if (message.length() != 0) {
                  Messages.showErrorDialog(project, message, "Failed to Run");
                  selectedTaskFile.drawAllWindows(selectedEditor);
                  ProjectView.getInstance(project).refresh();
                  return;
                }
              }

              selectedTaskFile.drawAllWindows(selectedEditor);
              String result = String.format("%d from %d tests failed", testNum - testPassed, testNum);
              createTestResultPopUp(result, JBColor.RED, project);
            }
          }
        }, null, null);
      }
    });
  }

  private void check(Project project,
                     TaskWindow taskWindow,
                     VirtualFile answerFile,
                     TaskFile answerTaskFile,
                     TaskFile usersTaskFile,
                     Document usersDocument,
                     StudyTestRunner testRunner) {

    try {
      VirtualFile windowCopy = answerFile.copy(this, answerFile.getParent(), "window" + taskWindow.getIndex() + ".py");
      final FileDocumentManager documentManager = FileDocumentManager.getInstance();
      final Document windowDocument = documentManager.getDocument(windowCopy);
      if (windowDocument != null) {
        TaskFile windowTaskFile = new TaskFile();
        TaskFile.copy(answerTaskFile, windowTaskFile);
        StudyDocumentListener listener = new StudyDocumentListener(windowTaskFile);
        windowDocument.addDocumentListener(listener);
        int start = taskWindow.getRealStartOffset(windowDocument);
        int end = start + taskWindow.getLength();
        TaskWindow userTaskWindow = usersTaskFile.getTaskWindows().get(taskWindow.getIndex());
        int userStart = userTaskWindow.getRealStartOffset(usersDocument);
        int userEnd = userStart + userTaskWindow.getLength();
        String text = usersDocument.getText(new TextRange(userStart, userEnd));
        windowDocument.replaceString(start, end, text);
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            documentManager.saveDocument(windowDocument);
          }
        });
        Process smartTestProcess = testRunner.launchTests(project, windowCopy.getNameWithoutExtension());
        boolean res = testRunner.testsPassed(smartTestProcess);
        userTaskWindow.setStatus(res ? StudyStatus.Solved : StudyStatus.Failed);
        windowCopy.delete(this);
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    catch (ExecutionException e) {
      e.printStackTrace();
    }
  }


  private VirtualFile getCopyWithAnswers(final VirtualFile taskDir,
                                         final VirtualFile file,
                                         final TaskFile source,
                                         TaskFile target) {
    VirtualFile copy = null;
    try {

      copy = file.copy(this, taskDir, "answers.py");
      final FileDocumentManager documentManager = FileDocumentManager.getInstance();
      final Document document = documentManager.getDocument(copy);
      if (document != null) {
        TaskFile.copy(source, target);
        StudyDocumentListener listener = new StudyDocumentListener(target);
        document.addDocumentListener(listener);
        for (TaskWindow taskWindow : target.getTaskWindows()) {
          final int start = taskWindow.getRealStartOffset(document);
          final int end = start + taskWindow.getLength();
          final String text = taskWindow.getPossibleAnswer();
          document.replaceString(start, end, text);
        }
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            documentManager.saveDocument(document);
          }
        });
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }


    return copy;
  }

  private void createTestResultPopUp(String text, Color color, Project project) {
    BalloonBuilder balloonBuilder =
      JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(text, null, color, null);
    Balloon balloon = balloonBuilder.createBalloon();
    JButton checkButton = StudyEditor.getSelectedStudyEditor(project).getCheckButton();
    balloon.showInCenterOf(checkButton);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    check(e.getProject());
  }
}
