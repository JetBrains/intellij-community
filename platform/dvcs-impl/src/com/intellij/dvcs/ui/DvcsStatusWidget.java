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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;

public abstract class DvcsStatusWidget<T extends Repository> extends EditorBasedWidget
  implements StatusBarWidget.MultipleTextValuesPresentation, StatusBarWidget.Multiframe {
  protected static final Logger LOG = Logger.getInstance(DvcsStatusWidget.class);
  private static final String MAX_STRING = "VCS: Rebasing feature-12345 in custom development branch";

  @NotNull private final String myPrefix;

  @Nullable private String myText;
  @Nullable private String myTooltip;
  @Nullable private Icon myIcon;

  protected DvcsStatusWidget(@NotNull Project project, @NotNull String prefix) {
    super(project);
    myPrefix = prefix;

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

    int maxLength = MAX_STRING.length() - 1; // -1, because there are arrows indicating that it is a popup
    myText = StringUtil.shortenTextWithEllipsis(getFullBranchName(repository), maxLength, 5);
    myTooltip = getToolTip(repository);
    myIcon = getIcon(repository);
    if (myStatusBar != null) {
      myStatusBar.updateWidget(ID());
    }
    rememberRecentRoot(repository.getRoot().getPath());
  }

  @Nullable
  @CalledInAwt
  private String getToolTip(@Nullable T repository) {
    if (repository == null) return null;
    String branchName = myPrefix + " Branch: " + getFullBranchName(repository);
    if (isMultiRoot(repository.getProject())) {
      return branchName + "\n" + "Root: " + repository.getRoot().getName();
    }
    return branchName;
  }
}
