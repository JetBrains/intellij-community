// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

import static com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR;
import static com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT;

public abstract class EditorAction extends AnAction implements DumbAware, UpdateInBackground {
  private static final Logger LOG = Logger.getInstance(EditorAction.class);

  private EditorActionHandler myHandler;
  private boolean myHandlersLoaded;

  public final EditorActionHandler getHandler() {
    ensureHandlersLoaded();
    return myHandler;
  }

  protected EditorAction(EditorActionHandler defaultHandler) {
    myHandler = defaultHandler;
    setEnabledInModalContext(true);
  }

  public final EditorActionHandler setupHandler(@NotNull EditorActionHandler newHandler) {
    ensureHandlersLoaded();
    EditorActionHandler tmp = myHandler;
    myHandler = newHandler;
    myHandler.setWorksInInjected(isInInjectedContext());
    return tmp;
  }

  private void ensureHandlersLoaded() {
    if (!myHandlersLoaded) {
      myHandlersLoaded = true;
      final String id = ActionManager.getInstance().getId(this);
      List<EditorActionHandlerBean> extensions = EditorActionHandlerBean.EP_NAME.getExtensionList();
      for (int i = extensions.size() - 1; i >= 0; i--) {
        final EditorActionHandlerBean handlerBean = extensions.get(i);
        if (handlerBean.action.equals(id)) {
          myHandler = handlerBean.getHandler(myHandler);
          myHandler.setWorksInInjected(isInInjectedContext());
        }
      }
    }
  }

  @Override
  public void setInjectedContext(boolean worksInInjected) {
    super.setInjectedContext(worksInInjected);
    // we assume that this method is called in constructor at the point
    // where the chain of handlers is not initialized yet
    // and it's enough to pass the flag to the default handler only
    myHandler.setWorksInInjected(isInInjectedContext());
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Editor editor = getEditor(dataContext);
    if (this instanceof LatencyAwareEditorAction && editor != null) {
      String actionId = ActionManager.getInstance().getId(this);
      InputEvent inputEvent = e.getInputEvent();
      if (actionId != null && inputEvent != null) {
        LatencyRecorder.getInstance().recordLatencyAwareAction(editor, actionId, inputEvent.getWhen());
      }
    }
    actionPerformed(editor, dataContext);
  }

  @Nullable
  protected Editor getEditor(@NotNull DataContext dataContext) {
    return EDITOR.getData(dataContext);
  }

  public final void actionPerformed(final Editor editor, @NotNull final DataContext dataContext) {
    if (editor == null) return;
    if (editor.isDisposed()) {
      VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
      LOG.error("Action " + this + " invoked on a disposed editor" + (file == null ? "" : " for file " + file));
      return;
    }
    final EditorActionHandler handler = getHandler();
    Runnable command = () -> handler.execute(editor, null, getProjectAwareDataContext(editor, dataContext));

    if (!handler.executeInCommand(editor, dataContext)) {
      command.run();
      return;
    }

    String commandName = getTemplatePresentation().getText();
    if (commandName == null) commandName = "";
    CommandProcessor.getInstance().executeCommand(editor.getProject(),
                                                  command,
                                                  commandName,
                                                  handler.getCommandGroupId(editor),
                                                  UndoConfirmationPolicy.DEFAULT,
                                                  editor.getDocument());
  }

  public void update(Editor editor, Presentation presentation, DataContext dataContext) {
    presentation.setEnabled(getHandler().isEnabled(editor, null, dataContext));
  }

  public void updateForKeyboardAccess(Editor editor, Presentation presentation, DataContext dataContext) {
    update(editor, presentation, dataContext);
  }

  @Override
  public void beforeActionPerformedUpdate(@NotNull AnActionEvent e) {
    if (isInInjectedContext()) {
      Editor editor = CommonDataKeys.HOST_EDITOR.getData(e.getDataContext());
      if (editor != null) {
        for (Caret caret : editor.getCaretModel().getAllCarets()) {
          if (EditorActionHandler.ensureInjectionUpToDate(caret)) {
            break;
          }
        }
      }
    }
    super.beforeActionPerformedUpdate(e);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    DataContext dataContext = e.getDataContext();
    Editor editor = getEditor(dataContext);
    if (editor == null) {
      presentation.setEnabled(false);
    }
    else {
      if (editor.isDisposed()) {
        LOG.error("Disposed editor in " + dataContext + " for " + this);
        presentation.setEnabled(false);
      }
      else {
        if (e.getInputEvent() instanceof KeyEvent) {
          updateForKeyboardAccess(editor, presentation, dataContext);
        }
        else {
          update(editor, presentation, dataContext);
        }
      }
    }
  }

  private static DataContext getProjectAwareDataContext(final Editor editor, @NotNull final DataContext original) {
    if (PROJECT.getData(original) == editor.getProject()) {
      return new DialogAwareDataContext(original);
    }

    return dataId -> {
      if (PROJECT.is(dataId)) {
        final Project project = editor.getProject();
        if (project != null) {
          return project;
        }
      }
      return original.getData(dataId);
    };
  }
}
