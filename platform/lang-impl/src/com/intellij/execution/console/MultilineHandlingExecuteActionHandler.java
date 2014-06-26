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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class MultilineHandlingExecuteActionHandler extends BaseConsoleExecuteActionHandler {
  @NotNull
  private BaseConsoleExecuteActionHandler myDelegateHandler;
  @Nullable
  private String myMultilineToken = null;
  @NotNull
  protected StringBuilder myInputBuffer;

  public MultilineHandlingExecuteActionHandler(@NotNull BaseConsoleExecuteActionHandler delegateHandler, boolean preserveMarkup) {
    super(preserveMarkup);
    myDelegateHandler = delegateHandler;
    initInputBuffer();
  }

  @NotNull
  protected abstract Collection<String> getMultilineTokens();

  protected abstract boolean startsAnotherCodeFragment(@NotNull String line);

  protected abstract void onMultilineEnter();

  protected abstract void onMultilineExit();

  protected abstract void onLineContinuation();

  protected abstract boolean processLineIfShouldNotExecute(@NotNull String line);


  @Nullable
  protected String getOpeningMultilineToken(@NotNull String line) {
    for (String token : getMultilineTokens()) {
      if (isMultilineStarts(line, token)) {
        return token;
      }
    }
    return null;
  }

  protected boolean closesThisMultilineToken(@NotNull String line, @NotNull String token) {
    return isMultilineStarts(line, token);
  }

  @Override
  protected void execute(@NotNull String text, @NotNull LanguageConsoleView console) {
    if (text.isEmpty()) {
      processOneLine(text, console);
    }
    else {
      if (StringUtil.countNewLines(text.trim()) > 0) {
        executeMultiLine(text, console);
      }
      else {
        processOneLine(text, console);
      }
    }
  }

  protected final void executeMultiLine(@NotNull String text, @NotNull LanguageConsoleView console) {
    myInputBuffer.append(text);

    myDelegateHandler.execute(myInputBuffer.toString(), console);
  }

  protected final void processOneLine(@NotNull String line, @NotNull LanguageConsoleView console) {
    line = StringUtil.trimTrailing(line);
    if (StringUtil.isEmptyOrSpaces(line)) {
      doProcessLine("\n", console);
    }
    else if (startsAnotherCodeFragment(line)) {
      doProcessLine("\n", console);
      doProcessLine(line, console);
    }
    else {
      doProcessLine(line, console);
    }
  }

  protected final void doProcessLine(@NotNull final String line, @NotNull LanguageConsoleView console) {
    if (!StringUtil.isEmptyOrSpaces(line)) {
      myInputBuffer.append(line);
      if (!line.endsWith("\n")) {
        myInputBuffer.append("\n");
      }
    }
    else if (StringUtil.isEmptyOrSpaces(myInputBuffer.toString())) {
      myInputBuffer.append("");
    }

    // multiline strings handling
    if (myMultilineToken == null) {
      myMultilineToken = getOpeningMultilineToken(line);

      if (myMultilineToken != null) {
        // change language
        onMultilineEnter();
        return;
      }
    }
    else {
      if (closesThisMultilineToken(line, myMultilineToken)) {
        myMultilineToken = null;
        // restore language
        onMultilineExit();
      }
      else {
        if(line.equals("\n")) {
          myInputBuffer.append("\n");
        }
        return;
      }
    }

    // Process line continuation
    if (line.endsWith("\\")) {
      onLineContinuation();
      return;
    }

    if (processLineIfShouldNotExecute(line)) {
      return;
    }

    myDelegateHandler.execute(myInputBuffer.toString(), console);
    initInputBuffer();
  }

  private void initInputBuffer() {
    myInputBuffer = new StringBuilder();
  }

  private static boolean isMultilineStarts(String line, String token) {
    return StringUtil.getOccurrenceCount(line, token) % 2 == 1;
  }

}
