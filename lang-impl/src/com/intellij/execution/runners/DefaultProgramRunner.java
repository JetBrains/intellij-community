package com.intellij.execution.runners;

import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileEditor.FileDocumentManager;

/**
 * @author spleaner
 */
public abstract class DefaultProgramRunner extends GenericProgramRunner {

  protected RunContentDescriptor doExecute(final RunProfileState state, final RunProfile runProfile, final Project project, final RunContentDescriptor contentToReuse,
                                           final RunnerSettings settings,
                                           final ConfigurationPerRunnerSettings configurationSettings) throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();
    ExecutionResult executionResult = state.execute(this);
    if (executionResult == null) return null;

    final RunContentBuilder contentBuilder = new RunContentBuilder(project, this);
    contentBuilder.setExecutionResult(executionResult);
    contentBuilder.setRunProfile(runProfile, settings, configurationSettings);
    return contentBuilder.showRunContent(contentToReuse);
  }


  public RunnerInfo getInfo() {
    return DEFAULT_RUNNER_INFO;
  }
}
