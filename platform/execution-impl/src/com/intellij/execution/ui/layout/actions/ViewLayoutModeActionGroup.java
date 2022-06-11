package com.intellij.execution.ui.layout.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.custom.options.CustomContentLayoutOptions;
import com.intellij.ui.content.custom.options.CustomContentLayoutOption;
import org.jetbrains.annotations.NotNull;

public class ViewLayoutModeActionGroup extends DefaultActionGroup implements ViewLayoutModificationAction {

  @NotNull
  private final Content myContent;

  public ViewLayoutModeActionGroup(
    @NotNull Content content,
    @NotNull CustomContentLayoutOptions customContentLayoutOptions) {
    super(customContentLayoutOptions.getDisplayName(), true);

    add(new ViewLayoutModeAction(new HideContentLayoutModeOption(content, customContentLayoutOptions)));
    for (CustomContentLayoutOption option : customContentLayoutOptions.getAvailableOptions()) {
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
    private final CustomContentLayoutOptions myOptions;

    public HideContentLayoutModeOption(Content content, CustomContentLayoutOptions options) {
      myContent = content;
      myOptions = options;
    }

    @Override
    public boolean isSelected() {
      return myOptions.isHidden();
    }

    @Override
    public void select() {
      myOptions.onHide();
    }

    @Override
    public boolean isEnabled() {
      return myOptions.isHideOptionVisible();
    }

    @Override
    public @NotNull String getDisplayName() {
      return IdeBundle.message("run.layout.do.not.show.view.option.message");
    }
  }
}
