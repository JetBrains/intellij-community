package ru.compscicenter.edide.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import ru.compscicenter.edide.StudyEditor;
import ru.compscicenter.edide.StudyTaskManager;
import ru.compscicenter.edide.course.Task;
import ru.compscicenter.edide.course.TaskFile;

import java.io.File;

/**
 * author: liana
 * data: 7/10/14.
 */
public class StudyRunAction extends AnAction {
  public void run(Project project) {
    Editor selectedEditor = StudyEditor.getSelectedEditor(project);
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());
    StudyTaskManager taskManager = StudyTaskManager.getInstance(selectedEditor.getProject());
    if (openedFile != null && openedFile.getCanonicalPath()!=null) {
      String filePath = openedFile.getCanonicalPath();
      GeneralCommandLine cmd = new GeneralCommandLine();
      cmd.setWorkDirectory(openedFile.getParent().getCanonicalPath());
      cmd.setExePath("python");
      cmd.addParameter(filePath);
      TaskFile selectedTaskFile = taskManager.getTaskFile(openedFile);
      Task currentTask = selectedTaskFile.getTask();
      if (currentTask.getInput() != null) {
        cmd.addParameter(currentTask.getInput());
      }
      try {
        Process p = cmd.createProcess();
        ProcessHandler handler = new OSProcessHandler(p);
        RunContentExecutor executor =  new RunContentExecutor(project, handler);
        executor.run();
      }
      catch (ExecutionException e) {
        e.printStackTrace();
      }
    }

  }
  public void actionPerformed(AnActionEvent e) {
    // TODO: insert action logic here
  }
}
