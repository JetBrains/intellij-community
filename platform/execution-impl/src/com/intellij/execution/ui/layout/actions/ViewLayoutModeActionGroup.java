package com.intellij.execution.ui.layout.actions;

import com.intellij.execution.ui.layout.impl.RunnerContentUi;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.custom.options.CustomContentLayoutOptions;
import com.intellij.ui.content.custom.options.CustomContentLayoutOption;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ViewLayoutModeActionGroup extends DefaultActionGroup implements ViewLayoutModificationAction {

  @NotNull
  private final Content myContent;

  public ViewLayoutModeActionGroup(
    @NotNull RunnerContentUi ui,
    @NotNull Content content) {
    super(content.getDisplayName(), true);

    CustomContentLayoutOptions customLayoutOptions = content.getUserData(CustomContentLayoutOptions.KEY);
    assert customLayoutOptions != null;

    add(new ViewLayoutModeAction(new HideContentLayoutModeOption(content, ui, customLayoutOptions)));
    for (CustomContentLayoutOption option : customLayoutOptions.getAvailableOptions()) {
      add(new ViewLayoutModeAction(option));
    }

    myContent = content;
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }

  @Override
  public @NotNull Content getContent() {
    return myContent;
  }

  public static class ViewLayoutModeAction extends DumbAwareToggleAction {

    private final CustomContentLayoutOption myOption;

    public ViewLayoutModeAction(
      @NotNull CustomContentLayoutOption option) {

      myOption = option;
    }

    @Override
    public boolean isDumbAware() {
      return true;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myOption.isSelected();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myOption.select();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(myOption.isEnabled());
      e.getPresentation().setText(myOption.getDisplayName());
    }
  }

  public static class HideContentLayoutModeOption implements CustomContentLayoutOption {

    private final Content myContent;
    private final RunnerContentUi myUi;
    private final CustomContentLayoutOptions myOptions;

    public HideContentLayoutModeOption(Content content, RunnerContentUi ui, CustomContentLayoutOptions options) {
      myContent = content;
      myUi = ui;
      myOptions = options;
    }

    @Override
    public boolean isSelected() {
      return !myContent.isValid() || Objects.requireNonNull(myContent.getManager()).getIndexOfContent(myContent) == -1;
    }

    @Override
    public void select() {
      myUi.minimize(myContent, null);
      myOptions.onHide();
    }

    @Override
    public boolean isEnabled() {
      return isSelected() || myUi.getContentManager().getContents().length > 1;
    }

    @Override
    public @NotNull String getDisplayName() {
      return IdeBundle.message("run.layout.do.not.show.view.option.message");
    }
  }
}
