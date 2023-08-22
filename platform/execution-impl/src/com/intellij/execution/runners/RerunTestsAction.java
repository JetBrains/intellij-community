// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runners;

import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reruns all registered execution sessions.<p>
 * The difference between this action and {@code Rerun} action (Ctrl+F5) is that this action reruns
 * only explicitly registered execution sessions. For example, their tabs can be hidden by other tabs, it doesn't matter.
 * <p>
 * Thus it can be convenient for rerunning tests, because running non-test execution session after
 * running test execution session won't hide the latter.
 *
 * @see RerunTestsNotification
 */
public class RerunTestsAction extends DumbAwareAction {
  public static final String ID = "RerunTests";
  private static final Set<RunContentDescriptor> REGISTRY = new HashSet<>();

  public static void register(@NotNull final RunContentDescriptor descriptor) {
    if (descriptor.getComponent() != null && REGISTRY.add(descriptor)) {
      Disposer.register(descriptor, new Disposable() {
        @Override
        public void dispose() {
          REGISTRY.remove(descriptor);
        }
      });
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    List<RunContentDescriptor> descriptors = new ArrayList<>(REGISTRY);
    for (RunContentDescriptor descriptor : descriptors) {
      if (descriptor.getComponent() == null) {
        REGISTRY.remove(descriptor);
      }
      else {
        Project project = e.getProject();
        if (project != null) {
          RunContentManager runContentManager = RunContentManager.getInstance(project);
          // check if the descriptor belongs to the current project
          if (runContentManager.getToolWindowByDescriptor(descriptor) != null) {
            ExecutionUtil.restart(descriptor);
          }
        }
      }
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(e.getProject() != null);
  }
}
