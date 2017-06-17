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
package com.intellij.execution.console;

import com.intellij.openapi.util.ModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Yuli Fiterman
 */
public interface ConsoleHistoryModel extends ModificationTracker {
  class TextWithOffset {
    private final String text;
    private final int offset;

    public TextWithOffset(String text, int offset) {
      this.text = text;
      this.offset = offset;
    }

    public String getText() {
      return text;
    }

    public int getOffset() {
      return offset;
    }
  }
  void resetEntries(@NotNull List<String> entries);

  void addToHistory(@Nullable String statement);

  int getMaxHistorySize();

  void removeFromHistory(String statement);

  @NotNull
  List<String> getEntries();

  boolean isEmpty();

  int getHistorySize();


  @Nullable
  TextWithOffset getHistoryNext();

  @Nullable
  TextWithOffset getHistoryPrev();

  boolean hasHistory();

  int getCurrentIndex();

  void setContent(@NotNull String userContent);

  /* if true then overrides the navigation behavior such that the down key on last line always navigates to prev instead of only when there
     are no more characters in from of the caret
   */
  default boolean prevOnLastLine() {
    return false;
  }

}
