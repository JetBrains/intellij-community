// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ui;

import com.intellij.codeWithMe.ClientId;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class AutoScrollFromSourceHandler {
  protected final Project myProject;
  protected final Alarm myAlarm;
  private final Disposable myParentDisposable;
  private final JComponent myComponent;

  public AutoScrollFromSourceHandler(@NotNull Project project, @NotNull JComponent view, @NotNull Disposable parentDisposable) {
    myProject = project;
    myComponent = view;
    myAlarm = new Alarm(parentDisposable);
    myParentDisposable = parentDisposable;
  }

  protected String getActionName() {
    return UIBundle.message("autoscroll.from.source.action.name");
  }

  protected String getActionDescription() {
    return UIBundle.message("autoscroll.from.source.action.description");
  }

  protected abstract boolean isAutoScrollEnabled();

  protected abstract void setAutoScrollEnabled(boolean enabled);

  protected abstract void selectElementFromEditor(@NotNull FileEditor editor);

  protected ModalityState getModalityState() {
    return ModalityState.current();
  }

  protected long getAlarmDelay() {
    return Registry.intValue("ide.autoscroll.from.source.delay", 100);
  }

  public void install() {
    final MessageBusConnection connection = myProject.getMessageBus().connect(myParentDisposable);
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        selectInAlarm(event.getNewEditor());
      }
    });
    updateCurrentSelection();
  }

  private void selectInAlarm(final FileEditor editor) {
    // Code WithMe: do not process changes from remote (client) editor switching
    if (!ClientId.isCurrentlyUnderLocalId()) return;

    if (editor != null && myComponent.isShowing() && isAutoScrollEnabled()) {
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(() -> selectElementFromEditor(editor), getAlarmDelay(), getModalityState());
    }
  }

  private void updateCurrentSelection() {
    FileEditor selectedEditor = FileEditorManager.getInstance(myProject).getSelectedEditor();
    if (selectedEditor != null) {
      ApplicationManager.getApplication().invokeLater(() -> selectInAlarm(selectedEditor), ModalityState.NON_MODAL, myProject.getDisposed());
    }
  }

  public ToggleAction createToggleAction() {
    return new AutoScrollFromSourceAction(getActionName(), getActionDescription());
  }

  private class AutoScrollFromSourceAction extends ToggleAction implements DumbAware {
    AutoScrollFromSourceAction(String actionName, String actionDescription) {
      super(actionName, actionDescription, AllIcons.General.AutoscrollFromSource);
    }

    @Override
    public boolean isSelected(@NotNull final AnActionEvent event) {
      return isAutoScrollEnabled();
    }

    @Override
    public void setSelected(@NotNull final AnActionEvent event, final boolean flag) {
      setAutoScrollEnabled(flag);
      updateCurrentSelection();
    }
  }
}

