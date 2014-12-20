package com.intellij.openapi.util.diff.api;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.util.diff.util.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public interface FrameDiffTool extends DiffTool {
  @CalledInAwt
  @NotNull
  DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request);

  interface DiffViewer extends Disposable {
    @NotNull
    JComponent getComponent();

    @Nullable
    JComponent getPreferredFocusedComponent();

    @NotNull
    @CalledInAwt
    ToolbarComponents init();
  }

  interface DiffContext extends UserDataHolder {
    @Nullable
    Project getProject();

    @NotNull
    DiffWindow getDiffWindow();

    interface DiffWindow {
      boolean isFocused();

      void requestFocus();
    }
  }

  class ToolbarComponents {
    @Nullable public List<AnAction> toolbarActions;
    @Nullable public List<AnAction> popupActions;
    @Nullable public JComponent statusPanel;
  }
}
