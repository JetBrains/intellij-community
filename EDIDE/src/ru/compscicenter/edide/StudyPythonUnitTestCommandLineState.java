package ru.compscicenter.edide;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.jetbrains.python.testing.unittest.PythonUnitTestCommandLineState;

public class StudyPythonUnitTestCommandLineState extends PythonUnitTestCommandLineState {
    public StudyPythonUnitTestCommandLineState(StudyUnitTestRunConfiguration runConfiguration, ExecutionEnvironment env) {
        super(runConfiguration, env);
    }

    private static final String UTRUNNER_PY = "utrunner.py";

    @Override
    protected String getRunner() {
        return UTRUNNER_PY;
    }

    @Override
    protected void addTestRunnerParameters(GeneralCommandLine cmd) throws ExecutionException {
        ParamsGroup script_params = cmd.getParametersList().getParamsGroup(GROUP_SCRIPT);
        assert script_params != null;
        script_params.addParameter(StudyPythonUnitTestCommandLineState.class.getResource(UTRUNNER_PY).getPath());
        addBeforeParameters(cmd);
        script_params.addParameters(getTestSpecs());
        addAfterParameters(cmd);
    }
}
