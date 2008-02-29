package com.intellij.execution.runners;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.openapi.util.JDOMExternalizable;

/**
 * @author spleaner
 */
public abstract class JavaPatchableProgramRunner<Settings extends JDOMExternalizable> extends GenericProgramRunner<Settings> {

  public abstract void patch(JavaParameters javaParameters, RunnerSettings settings, final boolean beforeExecution) throws ExecutionException;


}
