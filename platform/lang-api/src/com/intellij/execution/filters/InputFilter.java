/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.execution.filters;

import com.intellij.execution.ui.ConsoleViewContentType;
import org.jetbrains.annotations.Nullable;

public interface InputFilter {

  class Result {
    @Nullable
    protected String myText;
    @Nullable
    protected ConsoleViewContentType myConsoleViewContentType;

    /**
     * the filtered text will be excluded from printing
     */
    public Result() {
    }

    /**
     * @param text                   The text that will be printed, if null, then nothing will be printed.
     * @param consoleViewContentType The content type of printed text, if null, then original will be used.
     */
    public Result(@Nullable String text, @Nullable ConsoleViewContentType consoleViewContentType) {
      myText = text;
      myConsoleViewContentType = consoleViewContentType;
    }

    @Nullable
    public ConsoleViewContentType getConsoleViewContentType() {
      return myConsoleViewContentType;
    }

    @Nullable
    public String getText() {
      return myText;
    }
  }

  /**
   * @param text        The text to be filtered.
   * @param contentType The content type of filtered text
   * @return <tt>null</tt>, if there was no match, otherwise, an instance of {@link Result}.
   */
  @Nullable
  Result applyFilter(String text, ConsoleViewContentType contentType);

}
