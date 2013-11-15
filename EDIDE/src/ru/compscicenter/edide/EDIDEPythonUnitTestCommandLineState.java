package ru.compscicenter.edide;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.jetbrains.python.testing.attest.PythonAtTestCommandLineState;

public class EDIDEPythonUnitTestCommandLineState extends PythonAtTestCommandLineState {
    public EDIDEPythonUnitTestCommandLineState(EDIDEUnitTestRunConfiguration runConfiguration, ExecutionEnvironment env) {
        super(runConfiguration, env);
    }

    /*private static final String UTRUNNER_PY = "python/utrunner.py";

    @Override
    protected String getRunner() {
        return UTRUNNER_PY;
    }*/
}
