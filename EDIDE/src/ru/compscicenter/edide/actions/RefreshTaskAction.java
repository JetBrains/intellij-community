package ru.compscicenter.edide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import ru.compscicenter.edide.StudyEditor;
import ru.compscicenter.edide.StudyTaskManager;
import ru.compscicenter.edide.course.Task;
import ru.compscicenter.edide.course.TaskFile;
import ru.compscicenter.edide.course.Window;

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
                document.deleteString(0, document.getLineEndOffset(document.getLineCount() - 1));
                StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
                File resourceFile = new File(taskManager.getCourse().getResourcePath());
                File resourceRoot = resourceFile.getParentFile();
                FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
                VirtualFile openedFile = fileDocumentManager.getFile(document);
                TaskFile selectedTaskFile = taskManager.getTaskFile(openedFile);
                Task currentTask = selectedTaskFile.getTask();
                String lessonDir = "lesson" + String.valueOf(currentTask.getLesson().getIndex() + 1);
                String taskDir = "task" + String.valueOf(currentTask.getIndex() + 1);
                if (openedFile != null) {
                  File pattern = new File(new File(new File(resourceRoot, lessonDir), taskDir), openedFile.getName());
                  BufferedReader reader;
                  try {
                    reader = new BufferedReader(new InputStreamReader(new FileInputStream(pattern)));
                    int i = 0;
                    while (reader.ready()) {
                      document.insertString(document.getLineStartOffset(i), reader.readLine());
                      i++;
                    }
                    reader.close();
                  }
                  catch (FileNotFoundException e1) {
                    e1.printStackTrace();
                  }
                  catch (IOException e1) {
                    e1.printStackTrace();
                  }
                  for (Window window : selectedTaskFile.getWindows()) {
                    window.reset();
                  }
                  selectedTaskFile.drawAllWindows(editor);
                }
              }
            }, null, null);
          }
        });
      }
    });
  }

  public void actionPerformed(AnActionEvent e) {
    // TODO: insert action logic here
  }
}
