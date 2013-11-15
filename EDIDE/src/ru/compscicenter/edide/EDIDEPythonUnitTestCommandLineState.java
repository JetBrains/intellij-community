package ru.compscicenter.edide;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.jetbrains.python.testing.unittest.PythonUnitTestCommandLineState;

public class EDIDEPythonUnitTestCommandLineState extends PythonUnitTestCommandLineState {
    public EDIDEPythonUnitTestCommandLineState(EDIDEUnitTestRunConfiguration runConfiguration, ExecutionEnvironment env) {
        super(runConfiguration, env);
    }

    private static final String UTRUNNER_PY = "resources/utrunner.py";

    @Override
    protected String getRunner() {
        return UTRUNNER_PY;
    }
}
