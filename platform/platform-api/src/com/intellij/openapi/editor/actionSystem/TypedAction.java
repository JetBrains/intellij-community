/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Provides services for registering actions which are activated by typing in the editor.
 *
 * @see EditorActionManager#getTypedAction()
 */
public class TypedAction {
  private TypedActionHandler myHandler;
  private boolean myHandlersLoaded;

  public TypedAction() {
    myHandler = new Handler();
  }

  private void ensureHandlersLoaded() {
    if (!myHandlersLoaded) {
      myHandlersLoaded = true;
      for(EditorTypedHandlerBean handlerBean: Extensions.getExtensions(EditorTypedHandlerBean.EP_NAME)) {
        myHandler = handlerBean.getHandler(myHandler);
      }
    }
  }

  private static class Handler implements TypedActionHandler {
    public void execute(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
      if (editor.isViewer()) return;

      Document doc = editor.getDocument();
      Project project = PlatformDataKeys.PROJECT.getData(dataContext);
      if (!FileDocumentManager.getInstance().requestWriting(doc, project)) {
        return;
      }

      doc.startGuardedBlockChecking();
      try {
        final String str = String.valueOf(charTyped);
        CommandProcessor.getInstance().setCurrentCommandName(EditorBundle.message("typing.in.editor.command.name"));
        EditorModificationUtil.typeInStringAtCaretHonorBlockSelection(editor, str, true);
      }
      catch (ReadOnlyFragmentModificationException e) {
        EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(doc).handle(e);
      }
      finally {
        doc.stopGuardedBlockChecking();
      }
    }
  }

  /**
   * Gets the current typing handler.
   *
   * @return the current typing handler.
   */
  public TypedActionHandler getHandler() {
    ensureHandlersLoaded();
    return myHandler;
  }

  /**
   * Replaces the typing handler with the specified handler. The handler should pass
   * unprocessed typing to the previously registered handler.
   *
   * @param handler the handler to set.
   * @return the previously registered handler.
   */
  public TypedActionHandler setupHandler(TypedActionHandler handler) {
    ensureHandlersLoaded();
    TypedActionHandler tmp = myHandler;
    myHandler = handler;
    return tmp;
  }

  public final void actionPerformed(final Editor editor, final char charTyped, final DataContext dataContext) {
    if (editor == null) return;

    Runnable command = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new DocumentRunnable(editor.getDocument(),editor.getProject()) {
          public void run() {
            Document doc = editor.getDocument();
            doc.startGuardedBlockChecking();
            try {
              getHandler().execute(editor, charTyped, dataContext);
            }
            catch (ReadOnlyFragmentModificationException e) {
              EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(doc).handle(e);
            }
            finally {
              doc.stopGuardedBlockChecking();
            }
          }
        });
      }
    };

    CommandProcessor.getInstance().executeCommand(PlatformDataKeys.PROJECT.getData(dataContext), command, "", editor.getDocument(), UndoConfirmationPolicy.DEFAULT, editor.getDocument());
  }
}
