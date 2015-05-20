/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretAction;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for actions activated by keystrokes in the editor.
 * Implementations should override
 * {@link #execute(Editor, Caret, DataContext)}
 * .
 * <p>
 * Two types of handlers are supported: the ones which are executed once, and the ones which are executed for each caret. The latter can be
 * created using {@link EditorActionHandler#EditorActionHandler(boolean)} constructor.
 *
 * @see EditorActionManager#setActionHandler(String, EditorActionHandler)
 */
public abstract class EditorActionHandler {
  private final boolean myRunForEachCaret;
  private boolean myWorksInInjected;
  private boolean inExecution;
  private boolean inCheck;

  protected EditorActionHandler() {
    this(false);
  }

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
      final CaretTask check = new CaretTask() {
        @Override
        public void perform(@NotNull Caret caret, @Nullable DataContext dataContext) {
          result[0] = true;
        }
      };
      if (myRunForEachCaret) {
        hostEditor.getCaretModel().runForEachCaret(new CaretAction() {
          @Override
          public void perform(Caret caret) {
            doIfEnabled(caret, dataContext, check);
          }
        });
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
    DataContext caretContext = context == null ? null : new CaretSpecificDataContext(context, hostCaret);
    if (myWorksInInjected && caretContext != null) {
      DataContext injectedCaretContext = AnActionEvent.getInjectedDataContext(caretContext);
      Caret injectedCaret = CommonDataKeys.CARET.getData(injectedCaretContext);
      if (injectedCaret != null && injectedCaret != hostCaret && isEnabledForCaret(injectedCaret.getEditor(), injectedCaret, injectedCaretContext)) {
        task.perform(injectedCaret, injectedCaretContext);
        return;
      }
    }
    if (isEnabledForCaret(hostCaret.getEditor(), hostCaret, caretContext)) {
      task.perform(hostCaret, caretContext);
    }
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
      //noinspection deprecation
      return isEnabled(editor, dataContext);
    }
    finally {
      inCheck = false;
    }
  }

  /**
   * If <code>caret</code> is <code>null</code>, checks whether handler is enabled in general (i.e. enabled for at least one caret in editor),
   * if <code>caret</code> is not <code>null</code>, checks whether it's enabled for specified caret.
   */
  public final boolean isEnabled(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
    //noinspection deprecation
    return caret == null ? isEnabled(editor, dataContext) : isEnabledForCaret(editor, caret, dataContext);
  }
  /**
   * @deprecated To implement action logic, override
   * {@link #doExecute(Editor, Caret, DataContext)},
   * to invoke the handler, call
   * {@link #execute(Editor, Caret, DataContext)}.
   */
  public void execute(Editor editor, DataContext dataContext) {
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
   * @param caret       the caret for which the action is performed at the moment, or <code>null</code> if it's a 'one-off' action executed
   *                    without current context
   * @param dataContext the data context for the action.
   */
  protected void doExecute(Editor editor, @Nullable Caret caret, DataContext dataContext) {
    if (inExecution) {
      return;
    }
    try {
      inExecution = true;
      //noinspection deprecation
      execute(editor, dataContext);
    }
    finally {
      inExecution = false;
    }
  }

  public boolean executeInCommand(Editor editor, DataContext dataContext) {
    return true;
  }

  public boolean runForAllCarets() {
    return myRunForEachCaret;
  }

  /**
   * Executes the action in the context of given caret. If the caret is <code>null</code>, and the handler is a 'per-caret' handler,
   * it's executed for all carets.
   *
   * @param editor      the editor in which the action is invoked.
   * @param dataContext the data context for the action.
   */
  public final void execute(@NotNull Editor editor, @Nullable final Caret contextCaret, final DataContext dataContext) {
    Editor hostEditor = dataContext == null ? null : CommonDataKeys.HOST_EDITOR.getData(dataContext);
    if (hostEditor == null) {
      hostEditor = editor;
    }
    if (contextCaret == null && runForAllCarets()) {
      hostEditor.getCaretModel().runForEachCaret(new CaretAction() {
        @Override
        public void perform(Caret caret) {
          doIfEnabled(caret, dataContext, new CaretTask() {
            @Override
            public void perform(@NotNull Caret caret, @Nullable DataContext dataContext) {
              doExecute(caret.getEditor(), caret, dataContext);
            }
          });
        }
      });
    }
    else {
      if (contextCaret == null) {
        doIfEnabled(hostEditor.getCaretModel().getCurrentCaret(), dataContext, new CaretTask() {
          @Override
          public void perform(@NotNull Caret caret, @Nullable DataContext dataContext) {
            doExecute(caret.getEditor(), null, dataContext);
          }
        });
      }
      else {
        doExecute(editor, contextCaret, dataContext);
      }
    }
  }

  void setWorksInInjected(boolean worksInInjected) {
    myWorksInInjected = worksInInjected;
  }

  public DocCommandGroupId getCommandGroupId(Editor editor) {
    // by default avoid merging two consequential commands, and, in the same time, pass along the Document
    return DocCommandGroupId.noneGroupId(editor.getDocument());
  }

  private interface CaretTask {
    void perform(@NotNull Caret caret, @Nullable DataContext dataContext);
  }
}
