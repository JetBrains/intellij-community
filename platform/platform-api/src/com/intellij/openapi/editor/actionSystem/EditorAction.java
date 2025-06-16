// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actionSystem;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR;
import static com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT;

public abstract class EditorAction extends AnAction implements DumbAware, LightEditCompatible {
  private static final Logger LOG = Logger.getInstance(EditorAction.class);
  private static final Logger HANDLER_LOG = Logger.getInstance(EditorActionHandler.HANDLER_LOG_CATEGORY);

  private EditorActionHandler myHandler;
  private DynamicEditorActionHandler myDynamicHandler;

  protected EditorAction(EditorActionHandler defaultHandler) {
    myHandler = defaultHandler;
    setEnabledInModalContext(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  public final synchronized EditorActionHandler setupHandler(@NotNull EditorActionHandler newHandler) {
    if (HANDLER_LOG.isDebugEnabled()) {
      HANDLER_LOG.debug("Setup EditorActionHandler for " + this.getClass() + " with " + newHandler,
                        HANDLER_LOG.isTraceEnabled() ? new Throwable() : null);
    }

    EditorActionHandler tmp = getHandler();
    doSetupHandler(newHandler);
    return tmp;
  }

  private void doSetupHandler(@NotNull EditorActionHandler newHandler) {
    myHandler = newHandler;
    myHandler.setWorksInInjected(isInInjectedContext());
  }

  public final synchronized EditorActionHandler getHandler() {
    if (myDynamicHandler == null && myHandler != null) {
      doSetupHandler(myDynamicHandler = new DynamicEditorActionHandler(this, myHandler));
    }
    return myHandler;
  }

  @Override
  public synchronized void setInjectedContext(boolean worksInInjected) {
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

  protected @Nullable Editor getEditor(@NotNull DataContext dataContext) {
    return EDITOR.getData(dataContext);
  }

  public final void actionPerformed(final Editor editor, final @NotNull DataContext dataContext) {
    if (editor == null) return;
    if (editor.isDisposed()) {
      VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
      LOG.error("Action " + this + " invoked on a disposed editor" + (file == null ? "" : " for file " + file));
      return;
    }

    final EditorActionHandler handler = getHandler();
    if (!handler.executeInCommand(editor, dataContext)) {
      executeHandler(handler, editor, dataContext);
      return;
    }

    String commandName = getTemplatePresentation().getText();
    if (commandName == null) commandName = "";
    CommandProcessor.getInstance().executeCommand(editor.getProject(),
                                                  () -> executeHandler(handler, editor, dataContext),
                                                  commandName,
                                                  handler.getCommandGroupId(editor),
                                                  UndoConfirmationPolicy.DEFAULT,
                                                  editor.getDocument());
  }

  private void executeHandler(@NotNull EditorActionHandler handler, @NotNull Editor editor, @NotNull DataContext dataContext) {
    if (HANDLER_LOG.isDebugEnabled()) {
      HANDLER_LOG.debug("Started EditorAction: " + this.getClass() + " in " + editor,
                        HANDLER_LOG.isTraceEnabled() ? new Throwable() : null);
    }

    handler.execute(editor, null, getProjectAwareDataContext(editor, dataContext));

    if (HANDLER_LOG.isDebugEnabled()) {
      HANDLER_LOG.debug("Finished EditorAction: " + this.getClass() + " in " + editor,
                        HANDLER_LOG.isTraceEnabled() ? new Throwable() : null);
    }
  }

  public void update(Editor editor, Presentation presentation, DataContext dataContext) {
    presentation.setEnabled(getHandler().isEnabled(editor, null, dataContext));
  }

  public void updateForKeyboardAccess(Editor editor, Presentation presentation, DataContext dataContext) {
    update(editor, presentation, dataContext);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    DataContext dataContext = e.getDataContext();
    Editor editor = getEditor(dataContext);
    if (editor == null) {
      if (e.isFromContextMenu()) {
        presentation.setEnabledAndVisible(false);
      }
      else {
        presentation.setEnabled(false);
      }
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

  private static @NotNull DataContext getProjectAwareDataContext(@NotNull Editor editor, @NotNull DataContext original) {
    if (PROJECT.getData(original) == editor.getProject()) {
      return original;
    }
    return CustomizedDataContext.withSnapshot(original, sink -> sink.set(PROJECT, editor.getProject()));
  }

  public synchronized void clearDynamicHandlersCache() {
    if (myDynamicHandler != null) myDynamicHandler.clearCache();
  }

  public synchronized <T> @Nullable T getHandlerOfType(@NotNull Class<T> type) {
    EditorActionHandler handler = getHandler();
    if (handler != null) {
      T result = handler.getHandlerOfType(type);
      if (result != null) return result;
    }
    EditorActionHandler dynamicHandler = myDynamicHandler;
    if (dynamicHandler != null && dynamicHandler != handler) {
      return dynamicHandler.getHandlerOfType(type);
    }
    return null;
  }
}
