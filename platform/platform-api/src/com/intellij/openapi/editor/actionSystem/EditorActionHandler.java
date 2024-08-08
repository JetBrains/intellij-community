// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actionSystem;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.CustomizedDataContext;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for actions invoked in the editor.
 * Implementations should override {@link #doExecute(Editor, Caret, DataContext)}.
 * <p>
 * Two types of handlers are supported: the ones which are executed once, and the ones which are executed for each caret.
 * The latter can be created by extending the {@link ForEachCaret} class.
 *
 * @see EditorWriteActionHandler
 * @see EditorActionManager#setActionHandler(String, EditorActionHandler)
 */
public abstract class EditorActionHandler {
  static final String HANDLER_LOG_CATEGORY = "#com.intellij.openapi.editor.actionSystem.EditorActionHandler";
  protected static final Logger LOG = Logger.getInstance(HANDLER_LOG_CATEGORY);

  private final boolean myRunForEachCaret;
  private boolean myWorksInInjected;
  private boolean inExecution;
  private boolean inCheck;

  protected EditorActionHandler() {
    this(false);
  }

  /** Consider subclassing {@link ForEachCaret} instead. */
  protected EditorActionHandler(boolean runForEachCaret) {
    myRunForEachCaret = runForEachCaret;
  }

  /**
   * @deprecated Implementations should override
   * {@link #isEnabledForCaret(Editor, Caret, DataContext)}
   * instead,
   * client code should invoke
   * {@link #isEnabled(Editor, Caret, DataContext)}
   * instead.
   */
  @Deprecated
  public boolean isEnabled(Editor editor, final DataContext dataContext) {
    if (inCheck) {
      return true;
    }
    inCheck = true;
    try {
      if (editor == null) {
        return false;
      }
      Editor hostEditor = dataContext == null ? null : CommonDataKeys.HOST_EDITOR.getData(dataContext);
      if (hostEditor == null) {
        hostEditor = editor;
      }
      final boolean[] result = new boolean[1];
      final CaretTask check = (___, __) -> result[0] = true;
      if (myRunForEachCaret) {
        hostEditor.getCaretModel().runForEachCaret(caret -> doIfEnabled(caret, dataContext, check));
      }
      else {
        doIfEnabled(hostEditor.getCaretModel().getCurrentCaret(), dataContext, check);
      }
      return result[0];
    }
    finally {
      inCheck = false;
    }
  }

  private void doIfEnabled(@NotNull Caret hostCaret, @Nullable DataContext context, @NotNull CaretTask task) {
    DataContext caretContext = context == null ? null : caretDataContext(context, hostCaret);
    Editor editor = hostCaret.getEditor();
    if (myWorksInInjected && caretContext != null) {
      DataContext injectedCaretContext = AnActionEvent.getInjectedDataContext(caretContext);
      Caret injectedCaret = CommonDataKeys.CARET.getData(injectedCaretContext);
      if (injectedCaret != null && injectedCaret != hostCaret && isEnabledForCaret(injectedCaret.getEditor(), injectedCaret, injectedCaretContext)) {
        task.perform(injectedCaret, injectedCaretContext);
        return;
      }
    }
    if (isEnabledForCaret(editor, hostCaret, caretContext)) {
      task.perform(hostCaret, caretContext);
    }
  }

  static boolean ensureInjectionUpToDate(@NotNull Caret hostCaret) {
    Editor editor = hostCaret.getEditor();
    Project project = editor.getProject();
    if (project != null &&
        InjectedLanguageManager.getInstance(project).mightHaveInjectedFragmentAtOffset(editor.getDocument(), hostCaret.getOffset())) {
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
      return true;
    }
    return false;
  }

  /**
   * Implementations can override this method to define whether handler is enabled for a specific caret in a given editor.
   */
  protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    if (inCheck) {
      return true;
    }
    inCheck = true;
    try {
      return isEnabled(editor, dataContext);
    }
    finally {
      inCheck = false;
    }
  }

  /**
   * If {@code caret} is {@code null}, checks whether handler is enabled in general (i.e. enabled for at least one caret in editor),
   * if {@code caret} is not {@code null}, checks whether it's enabled for specified caret.
   */
  public final boolean isEnabled(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
    return caret == null ? isEnabled(editor, dataContext) : isEnabledForCaret(editor, caret, dataContext);
  }
  /**
   * @deprecated To implement action logic, override
   * {@link #doExecute(Editor, Caret, DataContext)},
   * to invoke the handler, call
   * {@link #execute(Editor, Caret, DataContext)}.
   */
  @Deprecated
  public void execute(@NotNull Editor editor, @Nullable DataContext dataContext) {
    if (inExecution) {
      return;
    }
    try {
      inExecution = true;
      execute(editor, editor.getCaretModel().getCurrentCaret(), dataContext);
    }
    finally {
      inExecution = false;
    }
  }

  /**
   * Executes the action in the context of given caret. Subclasses should override this method.
   *
   * @param editor      the editor in which the action is invoked.
   * @param caret       the caret for which the action is performed at the moment, or {@code null} if it's a 'one-off' action executed
   *                    without current context
   * @param dataContext the data context for the action.
   */
  protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
    if (inExecution) {
      return;
    }
    try {
      inExecution = true;
      execute(editor, dataContext);
    }
    finally {
      inExecution = false;
    }
  }

  public boolean executeInCommand(@NotNull Editor editor, DataContext dataContext) {
    return true;
  }

  public boolean runForAllCarets() {
    return myRunForEachCaret;
  }

  /**
   * Executes the action in the context of given caret. If the caret is {@code null}, and the handler is a 'per-caret' handler,
   * it's executed for all carets.
   *
   * @param editor      the editor in which the action is invoked.
   * @param dataContext the data context for the action.
   */
  public final void execute(@NotNull Editor editor, @Nullable Caret contextCaret, @Nullable DataContext dataContext) {
    if (LOG.isDebugEnabled()) {
      // The line will be logged multiple times for the same action. The last event in the chain is, typically, the 'actual event handler'.
      LOG.debug("Invoked handler " + this + " in " + editor + " for the caret " + contextCaret,
                LOG.isTraceEnabled() ? new Throwable() : null);
    }

    Editor hostEditor = dataContext == null ? null : CommonDataKeys.HOST_EDITOR.getData(dataContext);
    if (hostEditor == null) {
      hostEditor = editor;
    }
    if (contextCaret == null && runForAllCarets()) {
      hostEditor.getCaretModel().runForEachCaret(caret -> {
        DataContext refreshed = commitAndRefreshDataContextIfNeeded(dataContext, caret);
        doIfEnabled(caret, refreshed,
                    (c, dc) -> doExecute(c.getEditor(), c, dc));
      });
    }
    else if (contextCaret == null) {
      Caret caret = hostEditor.getCaretModel().getCurrentCaret();
      DataContext refreshed = commitAndRefreshDataContextIfNeeded(dataContext, caret);
      doIfEnabled(caret, refreshed,
                  (c, dc) -> doExecute(c.getEditor(), null, dc));
    }
    else {
      doExecute(editor, contextCaret, dataContext);
    }
  }

  private @Nullable DataContext commitAndRefreshDataContextIfNeeded(@Nullable DataContext dataContext, Caret caret) {
    // injected computed data must be refreshed after commit,
    // forced "caret" does that by adding a new snapshot layer
    return myWorksInInjected && ensureInjectionUpToDate(caret) && dataContext != null ?
           freshCaretDataContext(dataContext, caret) : dataContext;
  }

  void setWorksInInjected(boolean worksInInjected) {
    myWorksInInjected = worksInInjected;
  }

  public DocCommandGroupId getCommandGroupId(@NotNull Editor editor) {
    // by default avoid merging two consequential commands, and, in the same time, pass along the Document
    return DocCommandGroupId.noneGroupId(editor.getDocument());
  }

  <T> @Nullable T getHandlerOfType(@NotNull Class<T> type) {
    return type.isInstance(this) ? type.cast(this) : null;
  }

  @FunctionalInterface
  private interface CaretTask {
    void perform(@NotNull Caret caret, @Nullable DataContext dataContext);
  }

  public abstract static class ForEachCaret extends EditorActionHandler {
    protected ForEachCaret() {
      super(true);
    }

    @Override
    protected abstract void doExecute(@NotNull Editor editor,
                                      @SuppressWarnings("NullableProblems") @NotNull Caret caret,
                                      DataContext dataContext);
  }

  public static @NotNull DataContext caretDataContext(@NotNull DataContext context, @NotNull Caret caret) {
    if (CommonDataKeys.CARET.getData(context) == caret) return context;
    return freshCaretDataContext(context, caret);
  }

  @ApiStatus.Internal
  public static @NotNull DataContext freshCaretDataContext(@NotNull DataContext context, @NotNull Caret caret) {
    // reset and later recompute keys like injected caret
    return CustomizedDataContext.withSnapshot(context, sink -> {
      sink.set(CommonDataKeys.CARET, caret);
    });
  }
}
