// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.ui.layout.actions;

import com.intellij.execution.ui.layout.impl.RunnerContentUi;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.custom.options.ContentLayoutStateSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class RestoreViewAction extends DumbAwareToggleAction implements ViewLayoutModificationAction {

  private final Content myContent;
  private final ContentLayoutStateSettings myLayoutSettings;

  public RestoreViewAction(@NotNull RunnerContentUi ui, @NotNull Content content) {
    this(content, new DefaultContentStateSettings(ui, content));
  }

  public RestoreViewAction(@NotNull Content content, ContentLayoutStateSettings layoutSettings) {
    myContent = content;
    myLayoutSettings = layoutSettings;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return myLayoutSettings.isSelected();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    myLayoutSettings.setSelected(state);
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    super.update(e);
    e.getPresentation().setText(myLayoutSettings.getDisplayName());
    e.getPresentation().setEnabled(myLayoutSettings.isEnabled());
  }

  public @NotNull Content getContent() {
    return myContent;
  }

  private static class DefaultContentStateSettings implements ContentLayoutStateSettings {

    private final RunnerContentUi myUi;
    private final Content myContent;

    public DefaultContentStateSettings(@NotNull RunnerContentUi ui,
                                       @NotNull Content content) {
      myUi = ui;
      myContent = content;
    }

    @Override
    public boolean isSelected() {
      return myContent.isValid() && Objects.requireNonNull(myContent.getManager()).getIndexOfContent(myContent) != -1;
    }

    @Override
    public void setSelected(boolean state) {
      if (state) {
        myUi.restore(myContent);
        myUi.select(myContent, true);
      } else {
        myUi.minimize(myContent, null);
      }
    }

    @Override
    public void restore() {
      setSelected(true);
    }

    @Override
    public @NotNull String getDisplayName() {
      return myContent.getDisplayName();
    }

    @Override
    public boolean isEnabled() {
      return !isSelected() || myUi.getContentManager().getContents().length > 1;
    }
  }
}
