package com.intellij.database.actions;

import com.intellij.database.run.ui.EditMaximizedView;
import com.intellij.database.run.ui.EditorCellViewer;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.database.run.ui.EditMaximizedViewKt.findEditMaximized;

/**
 * @author Liudmila Kornilova
 **/
public class ToggleSoftWrapsInEditMaximize extends ToggleAction implements DumbAware {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabledAndVisible(getEditorViewer(e) != null);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    EditorCellViewer viewer = getEditorViewer(e);
    return viewer != null && viewer.isSoftWraps();
  }

  private static @Nullable EditorCellViewer getEditorViewer(@NotNull AnActionEvent e) {
    EditMaximizedView view = findEditMaximized(e.getDataContext());
    return view == null ? null : ObjectUtils.tryCast(view.getCurrentTabInfoProvider().getViewer(), EditorCellViewer.class);
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    EditorCellViewer viewer = getEditorViewer(e);
    if (viewer != null) {
      viewer.setSoftWraps(state);
    }
  }
}
