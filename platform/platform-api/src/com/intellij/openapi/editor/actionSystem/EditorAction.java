/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.KeyEvent;

public abstract class EditorAction extends AnAction implements DumbAware {
  private EditorActionHandler myHandler;
  private boolean myHandlersLoaded;

  public EditorActionHandler getHandler() {
    ensureHandlersLoaded();
    return myHandler;
  }

  protected EditorAction(EditorActionHandler defaultHandler) {
    myHandler = defaultHandler;
    setEnabledInModalContext(true);
  }

  public final EditorActionHandler setupHandler(EditorActionHandler newHandler) {
    ensureHandlersLoaded();
    EditorActionHandler tmp = myHandler;
    myHandler = newHandler;
    return tmp;
  }

  private void ensureHandlersLoaded() {
    if (!myHandlersLoaded) {
      myHandlersLoaded = true;
      final String id = ActionManager.getInstance().getId(this);
      EditorActionHandlerBean[] extensions = Extensions.getExtensions(EditorActionHandlerBean.EP_NAME);
      for (int i = extensions.length - 1; i >= 0; i--) {
        final EditorActionHandlerBean handlerBean = extensions[i];
        if (handlerBean.action.equals(id)) {
          myHandler = handlerBean.getHandler(myHandler);
        }
      }
    }
  }

  @Override
  public final void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Editor editor = getEditor(dataContext);
    actionPerformed(editor, dataContext);
  }

  @Nullable
  protected Editor getEditor(@NotNull DataContext dataContext) {
    return CommonDataKeys.EDITOR.getData(dataContext);
  }

  public final void actionPerformed(final Editor editor, @NotNull final DataContext dataContext) {
    if (editor == null) return;

    final EditorActionHandler handler = getHandler();
    Runnable command = new Runnable() {
      @Override
      public void run() {
        handler.execute(editor, getProjectAwareDataContext(editor, dataContext));
      }
    };

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
    presentation.setEnabled(getHandler().isEnabled(editor, dataContext));
  }

  public void updateForKeyboardAccess(Editor editor, Presentation presentation, DataContext dataContext) {
    update(editor, presentation, dataContext);
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    DataContext dataContext = e.getDataContext();
    Editor editor = getEditor(dataContext);
    if (editor == null) {
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

  private static DataContext getProjectAwareDataContext(final Editor editor, @NotNull final DataContext original) {
    if (CommonDataKeys.PROJECT.getData(original) == editor.getProject()) {
      return original;
    }

    return new DataContext() {
      @Override
      public Object getData(String dataId) {
        if (CommonDataKeys.PROJECT.is(dataId)) {
          return editor.getProject();
        }
        return original.getData(dataId);
      }
    };
  }
}
