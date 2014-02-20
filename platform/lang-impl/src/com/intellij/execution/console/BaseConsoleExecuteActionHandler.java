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
package com.intellij.execution.console;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseConsoleExecuteActionHandler extends ConsoleExecuteAction.ConsoleExecuteActionHandler {
  public BaseConsoleExecuteActionHandler(boolean preserveMarkup) {
    super(preserveMarkup);
  }

  public void runExecuteAction(@NotNull LanguageConsoleView consoleView) {
    runExecuteAction(consoleView.getConsole(), consoleView);
  }

  @Override
  final void doExecute(@NotNull String text, @NotNull LanguageConsoleImpl console, @Nullable LanguageConsoleView consoleView) {
    if (consoleView == null) {
      //noinspection deprecation
      execute(text);
    }
    else {
      execute(text, consoleView);
    }
  }

  protected void execute(@NotNull String text, @NotNull LanguageConsoleView console) {
    //noinspection deprecation
    execute(text);
  }

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  /**
   * @deprecated to remove in IDEA 15
   */
  public void runExecuteAction(@NotNull LanguageConsoleImpl languageConsole) {
    runExecuteAction(languageConsole, null);
  }

  @Deprecated
  /**
   * @deprecated to remove in IDEA 15
   */
  protected void execute(@NotNull String text) {
    throw new AbstractMethodError();
  }

  @Deprecated
  /**
   * @deprecated to remove in IDEA 14
   *
   * Never used. It is Python specific implementation.
   */
  public void finishExecution() {
  }

  public String getEmptyExecuteAction() {
    return ConsoleExecuteAction.CONSOLE_EXECUTE_ACTION_ID;
  }
}