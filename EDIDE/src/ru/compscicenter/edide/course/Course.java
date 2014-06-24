package ru.compscicenter.edide.course;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.List;

/**
 * User: lia
 * Date: 21.06.14
 * Time: 19:00
 */
public class Course {

  private List<Lesson> lessons;
  private String description;
  private String name;
  public List<Lesson> getLessons() {
    return lessons;
  }

  public void setLessons(List<Lesson> lessons) {
    this.lessons = lessons;
  }

  public void create(final Project project, final VirtualFile baseDir, final String resourseRoot) {
    ApplicationManager.getApplication().invokeLater(
      new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              try {
                VirtualFile courseDir = baseDir.createChildDirectory(this, "course");
                for (int i = 0; i < lessons.size(); i++) {
                  lessons.get(i).create(project, courseDir, i + 1, resourseRoot);
                }
              }
              catch (IOException e) {
                e.printStackTrace();
              }
              //          try {
              //            //StudyPlugin.createTaskManager(project.getName());
              //            //TaskManager taskManager = TaskManager.getInstance(project);
              //            TaskManager taskManager = TaskManager.getInstance(project);
              //            int tasksNumber = taskManager.getTasksNum();
              //            for (int task = 0; task < tasksNumber; task++) {
              //              VirtualFile taskDirectory = baseDir.createChildDirectory(this, "task" + (task + 1));
              //              for (int file = 0; file < taskManager.getTaskFileNum(task); file++) {
              //                final String curFileName = taskManager.getFileName(task, file);
              //                createFile(curFileName, taskDirectory);
              //              }
              //              final VirtualFile ideaDir = baseDir.findChild(".idea");
              //              if (ideaDir != null) {
              //                createFile(taskManager.getTest(task), ideaDir);
              //              }
              //              else {
              //                LOG.error("Could not find .idea directory");
              //              }
              //            }
              //          }
              //          catch (IOException e) {
              //            Log.print("Problems with creating files");
              //            Log.print(e.toString());
              //            Log.flush();
              //          }
              //          LocalFileSystem.getInstance().refresh(false);
              //        }
            }
          });
        }
      });
  }
}
