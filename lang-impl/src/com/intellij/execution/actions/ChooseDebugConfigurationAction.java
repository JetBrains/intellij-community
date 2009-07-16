package com.intellij.execution.actions;

import com.intellij.execution.Executor;

/**
 * @author spleaner
 */
public class ChooseDebugConfigurationAction extends ChooseRunConfigurationAction {

  @Override
  protected Executor getDefaultExecutor() {
    return super.getAlternateExecutor();
  }

  @Override
  protected Executor getAlternateExecutor() {
    return super.getDefaultExecutor();
  }

}
