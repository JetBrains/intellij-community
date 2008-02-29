package com.intellij.execution.runners;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;

/**
 * @author spleaner
 */
public abstract class DefaultProgramRunner extends GenericProgramRunner {

  protected RunContentDescriptor doExecute(final Executor executor,
                                           final RunProfileState state,
                                           final RunProfile runProfile,
                                           final Project project,
                                           final RunContentDescriptor contentToReuse,
                                           final RunnerSettings settings,
                                           final ConfigurationPerRunnerSettings configurationSettings) throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();
    ExecutionResult executionResult = state.execute(executor, this);
    if (executionResult == null) return null;

    final RunContentBuilder contentBuilder = new RunContentBuilder(project, this, executor);
    contentBuilder.setExecutionResult(executionResult);
    contentBuilder.setRunProfile(runProfile, settings, configurationSettings);
    return contentBuilder.showRunContent(contentToReuse);
  }

}
