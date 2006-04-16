/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;

/**
 * Provides services for registering actions which are activated by typing in the editor.
 *
 * @see EditorActionManager#getTypedAction()
 */
public class TypedAction {

  private static final Object TYPING_COMMAND_GROUP = Key.create("Typing");

  private TypedActionHandler myHandler;
  private final static PsiTreeChangeListener myCommitLogger = new PsiModificationTracker();

  public TypedAction() {
    myHandler = new Handler();
  }

  private static class Handler implements TypedActionHandler {
    public void execute(Editor editor, char charTyped, DataContext dataContext) {
      if (editor.isViewer()) return;

      Document doc = editor.getDocument();
      if (!doc.isWritable()) {
        if (!FileDocumentManager.fileForDocumentCheckedOutSuccessfully(doc, (Project)dataContext.getData(DataConstants.PROJECT))) {
          return;
        }
      }

      Project project = editor.getProject();
      doc.startGuardedBlockChecking();
      if (project != null) PsiManager.getInstance(project).addPsiTreeChangeListener(myCommitLogger);
      try {
        final String str = String.valueOf(charTyped);
        CommandProcessor.getInstance().setCurrentCommandName(EditorBundle.message("typing.in.editor.command.name"));
        EditorModificationUtil.typeInStringAtCaretHonorBlockSelection(editor, str, true);
      }
      catch (ReadOnlyFragmentModificationException e) {
        EditorActionManager.getInstance().getReadonlyFragmentModificationHandler().handle(e);
      }
      finally {
        if (project != null) PsiManager.getInstance(project).removePsiTreeChangeListener(myCommitLogger);
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
    TypedActionHandler tmp = myHandler;
    myHandler = handler;
    return tmp;
  }

  public final void actionPerformed(final Editor editor, final char charTyped, final DataContext dataContext) {
    if (editor == null) return;

    Runnable command = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            Document doc = editor.getDocument();
            doc.startGuardedBlockChecking();
            try {
              getHandler().execute(editor, charTyped, dataContext);
            }
            catch (ReadOnlyFragmentModificationException e) {
              EditorActionManager.getInstance().getReadonlyFragmentModificationHandler().handle(e);
            }
            finally {
              doc.stopGuardedBlockChecking();
            }
          }
        });
      }
    };

    CommandProcessor.getInstance().executeCommand((Project)dataContext.getData(DataConstants.PROJECT), command, "", TYPING_COMMAND_GROUP);
  }

  private static class PsiModificationTracker extends PsiTreeChangeAdapter {
    public void beforeChildAddition(PsiTreeChangeEvent event) {
      logError();
    }

    public void beforeChildRemoval(PsiTreeChangeEvent event) {
      logError();
    }

    public void beforeChildReplacement(PsiTreeChangeEvent event) {
      logError();
    }

    public void beforeChildMovement(PsiTreeChangeEvent event) {
      logError();
    }

    public void beforeChildrenChange(PsiTreeChangeEvent event) {
      logError();
    }

    public void beforePropertyChange(PsiTreeChangeEvent event) {
      logError();
    }
    private void logError() {
      //LOG.error("PSI should not be commited on every typing since this greatly reduces app responsiveness");
    }
  }
}
