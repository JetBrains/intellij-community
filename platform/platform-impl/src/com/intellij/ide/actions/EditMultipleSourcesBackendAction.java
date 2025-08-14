package com.intellij.ide.actions;

import com.intellij.idea.AppMode;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class EditMultipleSourcesBackendAction extends BaseNavigateToSourceAction implements ActionRemoteBehaviorSpecification.BackendOnly {
  public EditMultipleSourcesBackendAction() {
    super(true);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    // This action is a hack to make Enter work in the Project View,
    // so we only enable it there, and only when multiple sources are selected
    // (otherwise, it's handled on the frontend).
    if (!AppMode.isRemoteDevHost() || !inProjectView(e.getDataContext()) || !hasMultiple(getNavigatables(e.getDataContext()))) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    super.update(e);
  }

  private static boolean inProjectView(@NotNull DataContext context) {
    var toolWindow = context.getData(PlatformDataKeys.TOOL_WINDOW);
    if (toolWindow == null) return false;
    return toolWindow.getId().equals(ToolWindowId.PROJECT_VIEW);
  }

  private static boolean hasMultiple(Navigatable @Nullable [] navigatables) {
    if (navigatables == null) return false;
    return navigatables.length >= 2;
  }
}
