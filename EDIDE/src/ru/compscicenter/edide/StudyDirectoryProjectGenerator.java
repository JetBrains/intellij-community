package ru.compscicenter.edide;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Log;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;

/**
 * User: lia
 */
public class StudyDirectoryProjectGenerator implements DirectoryProjectGenerator {

    @Nls
    @NotNull
    @Override
    public String getName() {
        return "Study project";
    }

    @Nullable
    @Override
    public Object showGenerationSettings(VirtualFile baseDir) throws ProcessCanceledException {
        return null;
    }

    //should be invoked in invokeLater method
    public void createFile(String name, VirtualFile directory) throws IOException {
        VirtualFile currentFile = directory.createChildData(this, name);
        currentFile.setWritable(true);
        InputStream ip = StudyDirectoryProjectGenerator.class.getResourceAsStream(name);
        BufferedReader bf = new BufferedReader(new InputStreamReader(ip));
        OutputStream os = currentFile.getOutputStream(this);
        PrintWriter printWriter = new PrintWriter(os);
        while (bf.ready()) {
            String line = bf.readLine();
            if (bf.ready()) {
                printWriter.println(line);
            } else {
                printWriter.print(line);
            }
        }
        bf.close();
        printWriter.close();
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
                                        createFile(taskManager.getTest(task), baseDir.findChild(".idea"));

                                    }
                                    createFile("sum-input.txt", baseDir.findChild(".idea"));
                                    createFile("sum-input2.txt", baseDir.findChild(".idea"));
                                    createFile("sum-input3.txt", baseDir.findChild(".idea"));
                                } catch (IOException e) {
                                    Log.print("Problems with creating files");
                                    Log.print(e.toString());
                                    Log.flush();
                                }

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
