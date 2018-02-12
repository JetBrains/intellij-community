/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Expirable;
import com.intellij.openapi.wm.IdeFocusManager;

/**
 * @author peter
 */
class ActionTracker {
  private boolean myActionsHappened;
  private final Editor myEditor;
  private final Project myProject;
  private boolean myIgnoreDocumentChanges;

  ActionTracker(Editor editor, Disposable parentDisposable) {
    myEditor = editor;
    myProject = editor.getProject();
    ActionManager.getInstance().addAnActionListener(new AnActionListener.Adapter() {
      @Override
      public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        myActionsHappened = true;
      }
    }, parentDisposable);
    myEditor.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(DocumentEvent e) {
        if (!myIgnoreDocumentChanges) {
          myActionsHappened = true;
        }
      }
    }, parentDisposable);
  }

  void ignoreCurrentDocumentChange() {
    final CommandProcessor commandProcessor = CommandProcessor.getInstance();
    if (commandProcessor.getCurrentCommand() == null) return;

    myIgnoreDocumentChanges = true;
    commandProcessor.addCommandListener(new CommandListener() {
      @Override
      public void commandFinished(CommandEvent event) {
        commandProcessor.removeCommandListener(this);
        myIgnoreDocumentChanges = false;
      }
    });
  }

  boolean hasAnythingHappened() {
    return myActionsHappened || DumbService.getInstance(myProject).isDumb() ||
           myEditor.isDisposed() ||
           (myEditor instanceof EditorWindow && !((EditorWindow)myEditor).isValid());
  }

}
