// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ui;

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.dvcs.repo.VcsRepositoryMappingListener;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.event.MouseEvent;

public abstract class DvcsStatusWidget<T extends Repository> extends EditorBasedWidget
  implements StatusBarWidget.MultipleTextValuesPresentation, StatusBarWidget.Multiframe {
  protected static final Logger LOG = Logger.getInstance(DvcsStatusWidget.class);

  @NotNull private final String myVcsName;

  @Nullable private @Nls String myText;
  @Nullable private @NlsContexts.Tooltip String myTooltip;
  @Nullable private Icon myIcon;

  protected DvcsStatusWidget(@NotNull Project project, @NotNull @Nls String vcsName) {
    super(project);
    myVcsName = vcsName;

    project.getMessageBus().connect(this)
      .subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, new VcsRepositoryMappingListener() {
        @Override
        public void mappingChanged() {
          LOG.debug("repository mappings changed");
          updateLater();
        }
      });
  }

  @Nullable
  protected abstract T guessCurrentRepository(@NotNull Project project);

  @Nls
  @NotNull
  protected abstract String getFullBranchName(@NotNull T repository);

  @Nullable
  protected Icon getIcon(@NotNull T repository) {
    if (repository.getState() != Repository.State.NORMAL) return AllIcons.General.Warning;
    return AllIcons.Vcs.Branch;
  }

  protected abstract boolean isMultiRoot(@NotNull Project project);

  @NotNull
  protected abstract ListPopup getPopup(@NotNull Project project, @NotNull T repository);

  protected abstract void rememberRecentRoot(@NotNull String path);

  @Override
  public void install(@NotNull StatusBar statusBar) {
    super.install(statusBar);
    updateLater();
  }

  /**
   * @deprecated dvcs widgets are controlled by {@link com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager}
   * and cannot be removed manually
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public void deactivate() {
  }

  @Override
  public WidgetPresentation getPresentation() {
    return this;
  }

  @Override
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    LOG.debug("selection changed");
    update();
  }

  @Override
  public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    LOG.debug("file opened");
    update();
  }

  @Override
  public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    LOG.debug("file closed");
    update();
  }

  @CalledInAwt
  @Nullable
  @Override
  public String getSelectedValue() {
    return StringUtil.defaultIfEmpty(myText, "");
  }

  @Nullable
  @Override
  public String getTooltipText() {
    return myTooltip;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return myIcon;
  }

  @Nullable
  @Override
  public ListPopup getPopupStep() {
    if (isDisposed()) return null;
    Project project = getProject();
    T repository = guessCurrentRepository(project);
    if (repository == null) return null;

    return getPopup(project, repository);
  }

  @Nullable
  @Override
  public Consumer<MouseEvent> getClickConsumer() {
    // has no effect since the click opens a list popup, and the consumer is not called for the MultipleTextValuesPresentation
    return null;
  }

  protected void updateLater() {
    Project project = getProject();
    if (isDisposed()) return;
    ApplicationManager.getApplication().invokeLater(() -> {
      LOG.debug("update after repository change");
      update();
    }, project.getDisposed());
  }

  @CalledInAwt
  private void update() {
    myText = null;
    myTooltip = null;
    myIcon = null;

    if (isDisposed()) return;
    Project project = getProject();
    T repository = guessCurrentRepository(project);
    if (repository == null) return;

    // -1, because there are arrows indicating that it is a popup
    int maxLength = DvcsBundle.message("branch.popup.maximum.branch.length.sample").length() - 1;
    myText = StringUtil.shortenTextWithEllipsis(getFullBranchName(repository), maxLength, 5);
    myTooltip = getToolTip(repository);
    myIcon = getIcon(repository);
    if (myStatusBar != null) {
      myStatusBar.updateWidget(ID());
    }
    rememberRecentRoot(repository.getRoot().getPath());
  }

  @NlsContexts.Tooltip
  @Nullable
  @CalledInAwt
  private String getToolTip(@Nullable T repository) {
    if (repository == null) return null;
    String message = DvcsBundle.message("tooltip.branch.widget.vcs.branch.name.text", myVcsName, getFullBranchName(repository));
    if (isMultiRoot(repository.getProject())) {
      message += "\n";
      message += DvcsBundle.message("tooltip.branch.widget.root.name.text", repository.getRoot().getName());
    }
    return message;
  }
}
