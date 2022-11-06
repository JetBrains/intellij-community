// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ui;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.branch.DvcsBranchUtil;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.dvcs.repo.VcsRepositoryMappingListener;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class DvcsStatusWidget<T extends Repository> extends EditorBasedWidget
  implements StatusBarWidget.MultipleTextValuesPresentation, StatusBarWidget.Multiframe {
  protected static final Logger LOG = Logger.getInstance(DvcsStatusWidget.class);

  @NotNull private final String myVcsName;

  @Nullable private volatile @Nls String myText;
  @Nullable private volatile @NlsContexts.Tooltip String myTooltip;
  @Nullable private volatile Icon myIcon;
  private final Alarm myUpdateBackgroundAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);

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

  /**
   * @see DvcsUtil#guessWidgetRepository
   */
  @Nullable
  @CalledInAny
  protected abstract T guessCurrentRepository(@NotNull Project project, @Nullable VirtualFile selectedFile);

  @Nls
  @NotNull
  protected abstract String getFullBranchName(@NotNull T repository);

  @Nullable
  protected Icon getIcon(@NotNull T repository) {
    if (repository.getState() != Repository.State.NORMAL) return AllIcons.General.Warning;
    return AllIcons.Vcs.Branch;
  }

  protected abstract boolean isMultiRoot(@NotNull Project project);

  /**
   * @deprecated use {@link #getWidgetPopup(Project, Repository)}
   */
  @SuppressWarnings("unused")
  @Deprecated(forRemoval = true)
  @NotNull
  protected ListPopup getPopup(@NotNull Project project, @NotNull T repository) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  protected JBPopup getWidgetPopup(@NotNull Project project, @NotNull T repository) {
    return null;
  }

  protected abstract void rememberRecentRoot(@NotNull String path);

  @Override
  public void install(@NotNull StatusBar statusBar) {
    super.install(statusBar);
    updateLater();
  }

  @Override
  public WidgetPresentation getPresentation() {
    return this;
  }

  @Override
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    LOG.debug("selection changed");
    updateLater();
  }

  @Override
  public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    LOG.debug("file opened");
    updateLater();
  }

  @Override
  public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    LOG.debug("file closed");
    updateLater();
  }

  @RequiresEdt
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

  @Override
  public @Nullable JBPopup getPopup() {
    if (isDisposed()) return null;
    Project project = getProject();
    T repository = guessCurrentRepository(project, DvcsUtil.getSelectedFile(myProject));
    if (repository == null) return null;

    return getWidgetPopup(project, repository);
  }

  private void clearStatus() {
    myText = null;
    myTooltip = null;
    myIcon = null;
  }

  protected void updateLater() {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (isDisposed()) return;

      VirtualFile selectedFile = DvcsUtil.getSelectedFile(myProject);
      myUpdateBackgroundAlarm.cancelAllRequests();
      myUpdateBackgroundAlarm.addRequest(() -> {
        if (isDisposed()) {
          clearStatus();
          return;
        }

        if (!ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(() -> {
          updateOnBackground(selectedFile);
        })) {
          updateLater();
        }
      }, 10);
    });
  }

  @RequiresReadLock
  private void updateOnBackground(VirtualFile selectedFile) {
    T repository;
    try {
      Project project = getProject();
      repository = guessCurrentRepository(project, selectedFile);
      if (repository == null) {
        clearStatus();
        return;
      }
    }
    catch (ProcessCanceledException e) {
      // do nothing - a new update task is scheduled, or widget is disposed
      return;
    }
    catch (Throwable t) {
      LOG.error(t);
      clearStatus();
      return;
    }

    myText = DvcsBranchUtil.shortenBranchName(getFullBranchName(repository));
    myTooltip = getToolTip(repository);
    myIcon = getIcon(repository);
    if (myStatusBar != null) {
      myStatusBar.updateWidget(ID());
    }

    rememberRecentRoot(repository.getRoot().getPath());
  }

  @NlsContexts.Tooltip
  @Nullable
  protected String getToolTip(@Nullable T repository) {
    if (repository == null) return null;
    String message = DvcsBundle.message("tooltip.branch.widget.vcs.branch.name.text", myVcsName, getFullBranchName(repository));
    if (isMultiRoot(repository.getProject())) {
      message += "\n";
      message += DvcsBundle.message("tooltip.branch.widget.root.name.text", repository.getRoot().getName());
    }
    return message;
  }
}
