package ru.compscicenter.edide.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.sdk.PythonSdkType;
import ru.compscicenter.edide.StudyTaskManager;
import ru.compscicenter.edide.StudyUtils;
import ru.compscicenter.edide.course.Task;
import ru.compscicenter.edide.course.TaskFile;
import ru.compscicenter.edide.editor.StudyEditor;

public class StudyRunAction extends DumbAwareAction {
  public void run(Project project) {
    Editor selectedEditor = StudyEditor.getSelectedEditor(project);
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    assert selectedEditor != null;
    VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());
    StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    if (openedFile != null && openedFile.getCanonicalPath() != null) {
      String filePath = openedFile.getCanonicalPath();
      GeneralCommandLine cmd = new GeneralCommandLine();
      cmd.setWorkDirectory(openedFile.getParent().getCanonicalPath());
      Sdk sdk = PythonSdkType.findPythonSdk(ModuleManager.getInstance(project).getModules()[0]);
      if (sdk != null) {
        String pythonPath = sdk.getHomePath();
        if (pythonPath != null) {
          cmd.setExePath(pythonPath);
          cmd.addParameter(filePath);
          TaskFile selectedTaskFile = taskManager.getTaskFile(openedFile);
          assert selectedTaskFile != null;
          Task currentTask = selectedTaskFile.getTask();
          if (!currentTask.getUserTests().isEmpty()) {
            cmd.addParameter(StudyUtils.getFirst(currentTask.getUserTests()).getInput());
          }
          try {
            Process p = cmd.createProcess();
            ProcessHandler handler = new OSProcessHandler(p);

            RunContentExecutor executor = new RunContentExecutor(project, handler);
            executor.run();
          }

          catch (ExecutionException e) {
            e.printStackTrace();
          }
        }
      }
    }
  }

  public void actionPerformed(AnActionEvent e) {
    run(e.getProject());
  }
}
