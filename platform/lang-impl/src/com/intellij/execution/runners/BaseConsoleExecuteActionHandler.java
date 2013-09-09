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
package com.intellij.execution.runners;

import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.process.ConsoleHistoryModel;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

public abstract class BaseConsoleExecuteActionHandler {
  private boolean myAddCurrentToHistory = true;
  private final ConsoleHistoryModel myConsoleHistoryModel;
  private final boolean myPreserveMarkup;

  public BaseConsoleExecuteActionHandler(boolean preserveMarkup) {
    myConsoleHistoryModel = new ConsoleHistoryModel();
    myPreserveMarkup = preserveMarkup;
  }

  public ConsoleHistoryModel getConsoleHistoryModel() {
    return myConsoleHistoryModel;
  }

  public void setAddCurrentToHistory(boolean addCurrentToHistory) {
    myAddCurrentToHistory = addCurrentToHistory;
  }

  public void runExecuteAction(LanguageConsoleImpl languageConsole) {
    // process input and add to history
    Document document = languageConsole.getCurrentEditor().getDocument();
    String text = document.getText();
    TextRange range = new TextRange(0, document.getTextLength());

    languageConsole.getCurrentEditor().getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());

    if (myAddCurrentToHistory) {
      languageConsole.addCurrentToHistory(range, false, myPreserveMarkup);
    }

    languageConsole.setInputText("");

    UndoManager manager = languageConsole.getProject() == null ? UndoManager.getGlobalInstance() : UndoManager.getInstance(languageConsole.getProject());
    ((UndoManagerImpl)manager).invalidateActionsFor(DocumentReferenceManager.getInstance().create(document));

    myConsoleHistoryModel.addToHistory(text);
    execute(text);
  }

  protected abstract void execute(@NotNull String text);

  public void finishExecution() {
  }

  public String getEmptyExecuteAction() {
    return ConsoleExecuteAction.CONSOLE_EXECUTE_ACTION_ID;
  }
}