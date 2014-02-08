package ru.compscicenter.edide;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Log;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.platform.DirectoryProjectGenerator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;

/**
 * User: lia
 */
public class StudyDirectoryProjectGenerator implements DirectoryProjectGenerator {
    private RunManager runManager;
    private RunnerAndConfigurationSettings runConfiguration;

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

        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
                try {
                    TaskManager taskManager = TaskManager.getInstance();
                    int tasksNumber = taskManager.getTasksNum();
                    for (int task = 0; task < tasksNumber; task++) {
                        VirtualFile taskDirectory = baseDir.createChildDirectory(this, "task" + (task + 1));
                        for (int file = 0; file < taskManager.getTaskFileNum(task); file++) {
                            final String curFileName = taskManager.getFileName(task, file);
                            createFile(curFileName, taskDirectory);
                        }
                        createFile("task1_tests.py", baseDir.findChild("task1"));
                        createFile("study_utrunner.py", baseDir.findChild(".idea"));
                        createFile("study_tcunittest.py", baseDir.findChild(".idea"));

                    }

                } catch (IOException e) {
                    Log.print("Problems with creating files");
                    Log.print(e.toString());
                    Log.flush();
                }

            }
        });
        makeRunConfiguration(project, baseDir);

    }

    @NotNull
    @Override
    public ValidationResult validate(@NotNull String s) {
        return ValidationResult.OK;
    }
}
