package ru.compscicenter.edide;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Log;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * User: lia
 */
class StudyDirectoryProjectGenerator implements DirectoryProjectGenerator {
  private static final Logger LOG = Logger.getInstance(StudyDirectoryProjectGenerator.class.getName());

  @Nls
  @NotNull
  @Override
  public String getName() {
    return "Study project";
  }

  public static File getResourcesRoot() {
    @NonNls String jarPath = PathUtil.getJarPathForClass(StudyDirectoryProjectGenerator.class);
    if (jarPath.endsWith(".jar")) {
      final File jarFile = new File(jarPath);
      return jarFile.getParentFile().getParentFile();
    }

    return new File(jarPath);
  }

  @Nullable
  @Override
  public Object showGenerationSettings(VirtualFile baseDir) throws ProcessCanceledException {
    return null;
  }

  //should be invoked in invokeLater method
  void createFile(@NotNull final String name, @NotNull final VirtualFile directory) throws IOException {
    final File root = getResourcesRoot();
    String systemIndependentName = FileUtil.toSystemIndependentName(name);
    final int index = systemIndependentName.lastIndexOf("/");
    if (index > 0) {
      systemIndependentName = systemIndependentName.substring(index + 1);
    }
    FileUtil.copy(new File(root, name), new File(directory.getPath(), systemIndependentName));

  }

  @Override
  public void generateProject(@NotNull Project project, @NotNull final VirtualFile baseDir,
                              @Nullable Object settings, @NotNull Module module) {

    ApplicationManager.getApplication().invokeLater(
        new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                try {
                  TaskManager taskManager = TaskManager.getInstance();
                  taskManager.setCurrentTask(0);
                  int tasksNumber = taskManager.getTasksNum();
                  for (int task = 0; task < tasksNumber; task++) {
                    VirtualFile taskDirectory = baseDir.createChildDirectory(this, "task" + (task + 1));
                    for (int file = 0; file < taskManager.getTaskFileNum(task); file++) {
                      final String curFileName = taskManager.getFileName(task, file);
                      createFile(curFileName, taskDirectory);
                    }
                    final VirtualFile ideaDir = baseDir.findChild(".idea");
                    if (ideaDir != null)
                      createFile(taskManager.getTest(task), ideaDir);
                    else
                      LOG.error("Could not find .idea directory");
                  }
                } catch (IOException e) {
                  Log.print("Problems with creating files");
                  Log.print(e.toString());
                  Log.flush();
                }
                LocalFileSystem.getInstance().refresh(false);
              }
            });
          }
        }
    );
  }

  @NotNull
  @Override
  public ValidationResult validate(@NotNull String s) {
    return ValidationResult.OK;
  }
}
