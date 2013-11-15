package ru.compscicenter.edide;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.testing.unittest.PythonUnitTestRunConfiguration;
import org.jetbrains.annotations.NotNull;

public class EDIDEUnitTestRunConfiguration extends PythonUnitTestRunConfiguration {
    public EDIDEUnitTestRunConfiguration(Project project,
                                         ConfigurationFactory configurationFactory) {
        super(project, configurationFactory);
    }

    @Override
    public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
        return new EDIDEPythonUnitTestCommandLineState(this, env);
    }
}
