package ru.compscicenter.edide;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Log;
import com.intellij.openapi.editor.EditorFactory;
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
    static TaskManager taskManager = null;
    RunManager runManager;
    RunnerAndConfigurationSettings  runConfiguration;
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

    private void makeRunConfiguration(@NotNull Project project, @NotNull final VirtualFile baseDir) {
        runManager = RunManager.getInstance(project);
        StudyConfigurationType configurationType = ConfigurationTypeUtil.findConfigurationType(StudyConfigurationType.class);
        ConfigurationFactory[] factories = configurationType.getConfigurationFactories();
        runConfiguration = runManager.createRunConfiguration("Study test configuration", factories[0]);

        StudyUnitTestRunConfiguration configuration = (StudyUnitTestRunConfiguration) runConfiguration.getConfiguration();

        try {
            configuration.setScriptName(baseDir.findChild("task1").findChild("task1_tests.py").getCanonicalPath()); //TODO: get current task name
        } catch (Exception e) {
            Log.print("Can not find test script for run configuration");
        }

        runManager.addConfiguration(runConfiguration, true);
        runManager.setSelectedConfiguration(runConfiguration);
    }

    public void createFile(String name, VirtualFile directory) throws IOException {
        VirtualFile currentFile = directory.createChildData(this, name);
        currentFile.setWritable(true);
        InputStream ip = StudyDirectoryProjectGenerator.class.getResourceAsStream(name);
        BufferedReader bf = new BufferedReader(new InputStreamReader(ip));
        OutputStream os = currentFile.getOutputStream(this);
        PrintWriter printWriter = new PrintWriter(os);
        while (bf.ready()) {
            printWriter.println(bf.readLine());
        }
        bf.close();
        printWriter.close();
    }
    @Override
    public void generateProject(@NotNull Project project, @NotNull final VirtualFile baseDir,
                                @Nullable Object settings, @NotNull Module module) {
        try {
            InputStream metaIS = StudyDirectoryProjectGenerator.class.getResourceAsStream("tasks.meta");
            BufferedReader reader = new BufferedReader(new InputStreamReader(metaIS));
            final int tasksNumber = Integer.parseInt(reader.readLine());
            taskManager = TaskManager.getInstance();
            for (int task = 0; task < tasksNumber; task++) {

                int n = Integer.parseInt(reader.readLine());
                taskManager.addTask(n);
                for (int h = 0; h < n; h++) {
                     taskManager.setFileName(task, reader.readLine());
                }
                String taskTextFileName = Integer.toString(task + 1) + ".meta";
                System.out.println(taskTextFileName);
                InputStream taskTextIS = StudyDirectoryProjectGenerator.class.getResourceAsStream(taskTextFileName);
                System.out.println((taskTextIS == null));
                BufferedReader taskTextReader = new BufferedReader(new InputStreamReader(taskTextIS));
                System.out.println((taskTextReader == null));
                while(taskTextReader.ready()) {
                    taskManager.addTaskTextLine(task, taskTextReader.readLine());
                }

            }
            reader.close();

            ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (int task = 0; task < tasksNumber; task++) {
                            VirtualFile taskDirectory = baseDir.createChildDirectory(this, "task" + (task + 1));
                            for (int file = 0; file < taskManager.getTaskFileNum(task); file++) {
                                final String curFileName = taskManager.getFileName(task, file);
                                createFile(curFileName, taskDirectory);
                            }
                            createFile("task1_tests.py", baseDir.findChild("task1"));   //TODO: tests must copy with tasks

                        }
                    } catch (IOException e) {
                        Log.print("Problems with creating files");
                        Log.print(e.toString());
                        Log.flush();
                    }

                }
            });
            makeRunConfiguration(project, baseDir);
        } catch (IOException e) {
            Log.print("Problems with metadata file");
            Log.print(e.toString());
            Log.flush();
        }
    }

    @NotNull
    @Override
    public ValidationResult validate(@NotNull String s) {
        return ValidationResult.OK;
    }
}
