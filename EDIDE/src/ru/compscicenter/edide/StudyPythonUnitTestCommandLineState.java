package ru.compscicenter.edide;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.testing.unittest.PythonUnitTestCommandLineState;

import java.io.File;

public class StudyPythonUnitTestCommandLineState extends PythonUnitTestCommandLineState {
    public StudyPythonUnitTestCommandLineState(StudyUnitTestRunConfiguration runConfiguration, ExecutionEnvironment env) {
        super(runConfiguration, env);
    }

    private static final String UTRUNNER_PY = "study_utrunner.py";

    @Override
    protected String getRunner() {
        return UTRUNNER_PY;
    }

    @Override
    protected void addTestRunnerParameters(GeneralCommandLine cmd) throws ExecutionException {
        ParamsGroup script_params = cmd.getParametersList().getParamsGroup(GROUP_SCRIPT);
        assert script_params != null;
        File resourcePath = new File(ProjectManager.getInstance().getOpenProjects()[0].getBaseDir().getPath() , ".idea/study_utrunner.py");
        script_params.addParameter(resourcePath.getAbsolutePath());
        addBeforeParameters(cmd);
        script_params.addParameters(getTestSpecs());
        addAfterParameters(cmd);
    }
}
