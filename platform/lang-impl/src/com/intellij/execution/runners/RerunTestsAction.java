package com.intellij.execution.runners;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
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
  private static final List<RerunInfo> REGISTRY = ContainerUtil.createLockFreeCopyOnWriteList();

  public static void register(@NotNull RunContentDescriptor descriptor, @NotNull ExecutionEnvironment environment) {
    final RerunInfo rerunInfo = new RerunInfo(descriptor, environment);
    REGISTRY.add(rerunInfo);
    Disposer.register(descriptor, new Disposable() {
      @Override
      public void dispose() {
        REGISTRY.remove(rerunInfo);
      }
    });
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    for (RerunInfo rerunInfo : REGISTRY) {
      RunContentDescriptor descriptor = rerunInfo.getDescriptor();
      if (!Disposer.isDisposed(descriptor)) {
        ProcessHandler processHandler = descriptor.getProcessHandler();
        if (processHandler != null && processHandler.isProcessTerminated()) {
          ExecutionUtil.restart(rerunInfo.getEnvironment(), descriptor);
        }
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(true);
  }

  private static class RerunInfo {
    private final RunContentDescriptor myDescriptor;
    private final ExecutionEnvironment myEnv;

    public RerunInfo(@NotNull RunContentDescriptor descriptor, @NotNull ExecutionEnvironment env) {
      myDescriptor = descriptor;
      myEnv = env;
    }

    private RunContentDescriptor getDescriptor() {
      return myDescriptor;
    }

    private ExecutionEnvironment getEnvironment() {
      return myEnv;
    }
  }
}
