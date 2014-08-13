package com.intellij.execution.runners;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Reruns all registered execution sessions.<p>
 * The difference between this action and {@code Rerun} action (Ctrl+F5) is that this action reruns
 * only explicitly registered execution sessions. For example, their tabs can be hidden by other tabs, it doesn't matter.
 * <p>
 * Thus it can be convenient for rerunning tests, because running non-test execution session after
 * running test execution session won't hide the latter.
 *
 * @author Sergey Simonchik
 */
public class RerunTestsAction extends DumbAwareAction implements AnAction.TransparentUpdate {
  public static final String ID = "RerunTests";
  private static final List<ExecutionEnvironment> REGISTRY = ContainerUtil.createLockFreeCopyOnWriteList();

  public static void register(@NotNull final ExecutionEnvironment environment) {
    REGISTRY.add(environment);
    Disposer.register(environment, new Disposable() {
      @Override
      public void dispose() {
        REGISTRY.remove(environment);
      }
    });
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    for (ExecutionEnvironment environment : REGISTRY) {
      if (!Disposer.isDisposed(environment)) {
        RunContentDescriptor descriptor = environment.getContentToReuse();
        ProcessHandler processHandler = descriptor == null ? null : descriptor.getProcessHandler();
        if (processHandler != null && processHandler.isProcessTerminated()) {
          ExecutionUtil.restart(environment);
        }
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(true);
  }
}
