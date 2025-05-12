// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehavior;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsActions.ActionDescription;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ClientProperty;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;


public abstract class UndoRedoAction extends DumbAwareAction implements ActionRemoteBehaviorSpecification, LightEditCompatible {

  /**
   * Allow JB UndoManager for a JTextComponent (IJPL-8951)
   */
  public static final Key<Boolean> IGNORE_SWING_UNDO_MANAGER = new Key<>("IGNORE_SWING_UNDO_MANAGER");

  private static final Logger LOG = Logger.getInstance(UndoRedoAction.class);

  private boolean myActionInProgress;

  public UndoRedoAction() {
    setEnabledInModalContext(true);
  }

  protected abstract boolean isAvailable(FileEditor editor, UndoManager undoManager);

  protected abstract void perform(FileEditor editor, UndoManager undoManager);

  protected abstract Pair<@ActionText String, @ActionDescription String> getActionNameAndDescription(FileEditor editor, UndoManager undoManager);

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    FileEditor editor = PlatformCoreDataKeys.FILE_EDITOR.getData(dataContext);
    UndoManager undoManager = getUndoManager(editor, dataContext, false);
    if (undoManager == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(isAvailable(editor, undoManager));

    var pair = getActionNameAndDescription(editor, undoManager);
    presentation.setText(pair.first);
    presentation.setDescription(pair.second);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    DataContext dataContext = event.getDataContext();
    FileEditor editor = PlatformCoreDataKeys.FILE_EDITOR.getData(dataContext);
    UndoManager undoManager = getUndoManager(editor, dataContext, true);
    if (undoManager == null) {
      return;
    }
    myActionInProgress = true;
    try {
      perform(editor, undoManager);
    }
    finally {
      myActionInProgress = false;
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Internal
  @Override
  public @NotNull ActionRemoteBehavior getBehavior() {
    return UndoUtil.isExperimentalFrontendUndoEnabled()
      ? ActionRemoteBehavior.FrontendOtherwiseBackend // see `com.jetbrains.rdclient.command.FrontendUndoManager`
      : ActionRemoteBehavior.BackendOnly;
  }

  private @Nullable UndoManager getUndoManager(@Nullable FileEditor editor, DataContext dataContext, boolean isActionPerformed) {
    return getUndoManager(editor, dataContext, myActionInProgress, isActionPerformed);
  }

  static @Nullable UndoManager getUndoManager(
    @Nullable FileEditor editor,
    DataContext dataContext,
    boolean isActionInProgress,
    boolean isActionPerformed
  ) {
    Component component = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext);
    if (component instanceof JTextComponent && !ClientProperty.isTrue(component, IGNORE_SWING_UNDO_MANAGER)) {
      return SwingUndoManagerWrapper.fromContext(dataContext);
    }
    JRootPane rootPane = null;
    JBPopup popup = null;
    if (editor == null) {
      rootPane = UIUtil.getRootPane(component);
      popup = rootPane != null ? (JBPopup)rootPane.getClientProperty(JBPopup.KEY) : null;
      boolean modalPopup = popup != null && popup.isModalContext();
      boolean modalContext = Boolean.TRUE.equals(PlatformCoreDataKeys.IS_MODAL_CONTEXT.getData(dataContext));
      if (modalPopup || modalContext) {
        return SwingUndoManagerWrapper.fromContext(dataContext);
      }
    }
    if (isActionInProgress && isActionPerformed) {
      LOG.error(
        "Recursive undo invocation attempt, component: %s, fileEditor: %s, rootPane: %s, popup: %s"
          .formatted(component, editor, rootPane, popup)
      );
      return null;
    }
    Project project = getProject(editor, dataContext);
    return project != null && !project.isDefault() ? UndoManager.getInstance(project) : UndoManager.getGlobalInstance();
  }

  private static @Nullable Project getProject(FileEditor editor, DataContext dataContext) {
    if (editor instanceof TextEditor textEditor) {
      return textEditor.getEditor().getProject();
    }
    return CommonDataKeys.PROJECT.getData(dataContext);
  }
}
